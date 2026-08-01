package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Config.S3Properties;
import com.aivle26.aipm.Config.storage.S3DocumentObjectStorage;
import com.aivle26.aipm.Config.storage.DocumentStorageProperties;
import com.aivle26.aipm.Dto.project.ProjectDocumentUploadResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectDocument;
import com.aivle26.aipm.Entity.project.ProjectStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectDocumentServiceTest {
    private static final long PROJECT_ID = 42L;
    private static final String BUCKET = "aipm-test-bucket";

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectDocumentRepository projectDocumentRepository;
    @Mock
    private ProjectRequirementRepository projectRequirementRepository;
    @Mock
    private ProjectAuthorizationService projectAuthorizationService;
    @Mock
    private S3Client s3Client;
    @Mock
    private TransactionTemplate transactionTemplate;

    private ProjectDocumentService service;
    private Project project;

    @BeforeEach
    void setUp() {
        DocumentStorageProperties documentProperties = new DocumentStorageProperties();
        documentProperties.setMaxFileSize(10);
        documentProperties.setAllowedExtensions(List.of("pdf", "docx", "xlsx", "pptx", "txt"));
        documentProperties.setAllowedMimeTypes(List.of(
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "text/plain"
        ));
        S3Properties s3Properties = new S3Properties();
        s3Properties.setBucket(BUCKET);

        service = new ProjectDocumentService(
                projectRepository,
                projectDocumentRepository,
                projectRequirementRepository,
                documentProperties,
                projectAuthorizationService,
                new S3DocumentObjectStorage(s3Client, s3Properties),
                transactionTemplate
        );

        project = new Project();
        project.setId(PROJECT_ID);
        project.setStatus(ProjectStatus.DRAFT);
        lenient().when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            var callback = invocation.getArgument(
                    0,
                    org.springframework.transaction.support.TransactionCallback.class
            );
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        lenient().when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        lenient().when(projectDocumentRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<ProjectDocument> input = invocation.getArgument(0);
            List<ProjectDocument> saved = new ArrayList<>();
            input.forEach(saved::add);
            AtomicLong ids = new AtomicLong(1);
            saved.forEach(document -> document.setId(ids.getAndIncrement()));
            return saved;
        });
    }

    @Test
    void uploadStoresS3ObjectAndMetadata() {
        ProjectDocumentUploadResponse response =
                service.uploadInitialDocuments(PROJECT_ID, List.of(textFile()));

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key())
                .matches("projects/42/documents/[0-9a-f-]{36}/requirements\\.txt");
        assertThat(response.documents()).hasSize(1);
        assertThat(response.documents().getFirst().originalFileName()).isEqualTo("requirements.txt");
        verify(projectAuthorizationService).requireProjectPm(PROJECT_ID);
    }

    @Test
    void storedDocumentIsLoadedFromS3ForAiTransmission() {
        byte[] content = "hello".getBytes();
        ProjectDocument document = new ProjectDocument();
        document.setOriginalFileName("requirements.txt");
        document.setContentType("text/plain");
        document.setFileSize(content.length);
        document.setStoragePath("projects/42/documents/id/requirements.txt");
        when(projectDocumentRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of(document));
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
                ResponseBytes.fromByteArray(GetObjectResponse.builder().contentLength((long) content.length).build(), content)
        );

        var files = service.getStoredDocumentFiles(PROJECT_ID);

        assertThat(files).hasSize(1);
        assertThat(files.getFirst().originalFileName()).isEqualTo("requirements.txt");
        assertThat(files.getFirst().content()).isEqualTo(content);
        verify(s3Client, times(1))
                .getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    void pdfContentRequiresProjectAccessAndStreamsStoredBytes() {
        byte[] content = "%PDF-1.7".getBytes();
        ProjectDocument document = pdfDocument(77L);
        when(projectDocumentRepository.findByIdAndProjectId(77L, PROJECT_ID))
                .thenReturn(Optional.of(document));
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
                ResponseBytes.fromByteArray(
                        GetObjectResponse.builder()
                                .contentLength((long) content.length)
                                .build(),
                        content
                )
        );

        ProjectDocumentService.ProjectDocumentContent result =
                service.getPdfContent(PROJECT_ID, 77L);

        verify(projectAuthorizationService).requireProjectAccess(PROJECT_ID);
        assertThat(result.originalFileName()).isEqualTo("requirements.pdf");
        assertThat(result.contentType()).isEqualTo("application/pdf");
        assertThat(result.content()).isEqualTo(content);
    }

    @Test
    void pdfContentDoesNotAllowDocumentFromAnotherProject() {
        when(projectDocumentRepository.findByIdAndProjectId(77L, PROJECT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPdfContent(PROJECT_ID, 77L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Project document was not found");
        verify(s3Client, never()).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    void uploadRejectsMismatchedContentType() {
        MockMultipartFile file = new MockMultipartFile("files", "requirements.pdf", "text/plain", "abc".getBytes());

        assertThatThrownBy(() -> service.uploadInitialDocuments(PROJECT_ID, List.of(file)))
                .isInstanceOf(ApiException.class);
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    private ProjectDocument pdfDocument(Long documentId) {
        ProjectDocument document = new ProjectDocument();
        document.setId(documentId);
        document.setProject(project);
        document.setOriginalFileName("requirements.pdf");
        document.setStoredFileName("stored.pdf");
        document.setExtension("pdf");
        document.setContentType("application/pdf");
        document.setFileSize(8);
        document.setStoragePath("projects/42/documents/id/requirements.pdf");
        return document;
    }

    @Test
    void uploadDoesNotReplaceDocumentAfterRequirementsReferenceProjectDocuments() {
        ProjectDocument existing = new ProjectDocument();
        existing.setId(88L);
        existing.setProject(project);
        existing.setOriginalFileName("requirements.txt");
        existing.setStoragePath("projects/42/documents/old/requirements.txt");
        when(projectDocumentRepository.findByProjectIdAndOriginalFileName(
                PROJECT_ID,
                "requirements.txt"
        )).thenReturn(Optional.of(existing));
        when(projectRequirementRepository.existsByProjectId(PROJECT_ID))
                .thenReturn(true);

        assertThatThrownBy(() ->
                service.uploadInitialDocuments(PROJECT_ID, List.of(textFile())))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("PROJECT_DOCUMENT_IN_USE")
                );
        verify(s3Client, never()).putObject(
                any(PutObjectRequest.class),
                any(RequestBody.class)
        );
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void uploadRejectsUserWithoutProjectPermission() {
        doThrow(new AccessDeniedException("Access is denied"))
                .when(projectAuthorizationService).requireProjectPm(PROJECT_ID);

        assertThatThrownBy(() -> service.uploadInitialDocuments(PROJECT_ID, List.of(textFile())))
                .isInstanceOf(AccessDeniedException.class);
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void uploadFailureDoesNotSaveMetadata() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("S3 unavailable"));

        assertThatThrownBy(() -> service.uploadInitialDocuments(PROJECT_ID, List.of(textFile())))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("DOCUMENT_STORAGE_ERROR"));
        verify(projectDocumentRepository, never()).saveAll(any());
    }

    @Test
    void databaseFailureDeletesUploadedS3Object() {
        doThrow(new DataIntegrityViolationException("database unavailable"))
                .when(projectDocumentRepository).saveAll(any());

        assertThatThrownBy(() -> service.uploadInitialDocuments(PROJECT_ID, List.of(textFile())))
                .isInstanceOf(DataIntegrityViolationException.class);

        ArgumentCaptor<PutObjectRequest> put = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<DeleteObjectRequest> delete = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).putObject(put.capture(), any(RequestBody.class));
        verify(s3Client).deleteObject(delete.capture());
        assertThat(delete.getValue().key()).isEqualTo(put.getValue().key());
    }

    private MockMultipartFile textFile() {
        return new MockMultipartFile("files", "requirements.txt", "text/plain", "hello".getBytes());
    }
}
