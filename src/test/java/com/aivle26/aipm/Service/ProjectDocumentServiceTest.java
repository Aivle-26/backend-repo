package com.aivle26.aipm.Service;

import com.aivle26.aipm.Config.DocumentStorageProperties;
import com.aivle26.aipm.Config.S3Properties;
import com.aivle26.aipm.Dto.ProjectDocumentUploadResponse;
import com.aivle26.aipm.Entity.Project;
import com.aivle26.aipm.Entity.ProjectDocument;
import com.aivle26.aipm.Entity.ProjectStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    private ProjectAuthorizationService projectAuthorizationService;

    @Mock
    private S3Client s3Client;

    private ProjectDocumentService projectDocumentService;
    private Project project;
    private List<ProjectDocument> persistedDocuments;

    @BeforeEach
    void setUp() {
        DocumentStorageProperties documentProperties = new DocumentStorageProperties();
        documentProperties.setStoragePath("unused-with-s3");
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
        s3Properties.setRegion("ap-northeast-2");
        s3Properties.setBucket(BUCKET);

        projectDocumentService = new ProjectDocumentService(
                projectRepository,
                projectDocumentRepository,
                documentProperties,
                projectAuthorizationService,
                s3Client,
                s3Properties
        );

        project = new Project();
        project.setId(PROJECT_ID);
        project.setStatus(ProjectStatus.DRAFT);
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        lenient().when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        lenient().when(projectDocumentRepository.saveAllAndFlush(any())).thenAnswer(invocation -> {
            Iterable<ProjectDocument> documents = invocation.getArgument(0);
            persistedDocuments = new ArrayList<>();
            documents.forEach(persistedDocuments::add);
            AtomicLong id = new AtomicLong(1);
            persistedDocuments.forEach(document -> document.setId(id.getAndIncrement()));
            return persistedDocuments;
        });
    }

    @Test
    void uploadInitialDocumentsStoresS3ObjectAndMetadata() {
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "requirements final.txt",
                "text/plain",
                "hello".getBytes()
        );

        ProjectDocumentUploadResponse response =
                projectDocumentService.uploadInitialDocuments(PROJECT_ID, List.of(file));

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        PutObjectRequest putObjectRequest = requestCaptor.getValue();

        assertThat(response.projectId()).isEqualTo(PROJECT_ID);
        assertThat(response.documents()).hasSize(1);
        assertThat(response.documents().getFirst().documentId()).isEqualTo(1L);
        assertThat(response.documents().getFirst().originalFileName()).isEqualTo("requirements final.txt");
        assertThat(putObjectRequest.bucket()).isEqualTo(BUCKET);
        assertThat(putObjectRequest.key())
                .matches("projects/42/documents/[0-9a-f-]{36}/requirements_final\\.txt");
        assertThat(putObjectRequest.contentType()).isEqualTo("text/plain");
        assertThat(persistedDocuments).hasSize(1);
        assertThat(persistedDocuments.getFirst().getStoragePath()).isEqualTo(putObjectRequest.key());
        assertThat(persistedDocuments.getFirst().getStoredFileName())
                .matches("[0-9a-f-]{36}\\.txt");
        verify(projectAuthorizationService).requireProjectPm(PROJECT_ID);
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void uploadInitialDocumentsRejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("files", "empty.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> projectDocumentService.uploadInitialDocuments(PROJECT_ID, List.of(file)))
                .isInstanceOf(ApiException.class)
                .hasMessage("invalid file");

        verifyNoInteractions(projectDocumentRepository);
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void uploadInitialDocumentsRejectsUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "malware.exe",
                "application/octet-stream",
                "abc".getBytes()
        );

        assertThatThrownBy(() -> projectDocumentService.uploadInitialDocuments(PROJECT_ID, List.of(file)))
                .isInstanceOf(ApiException.class)
                .hasMessage("invalid file");

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void uploadInitialDocumentsRejectsMismatchedContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "requirements.pdf",
                "text/plain",
                "abc".getBytes()
        );

        assertThatThrownBy(() -> projectDocumentService.uploadInitialDocuments(PROJECT_ID, List.of(file)))
                .isInstanceOf(ApiException.class)
                .hasMessage("invalid file");

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void uploadInitialDocumentsRejectsOversizedFile() {
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "requirements.txt",
                "text/plain",
                new byte[11]
        );

        assertThatThrownBy(() -> projectDocumentService.uploadInitialDocuments(PROJECT_ID, List.of(file)))
                .isInstanceOf(ApiException.class)
                .hasMessage("file size exceeded");

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void uploadInitialDocumentsReturnsNotFoundForMissingProject() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());
        MockMultipartFile file = textFile();

        assertThatThrownBy(() -> projectDocumentService.uploadInitialDocuments(PROJECT_ID, List.of(file)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(projectAuthorizationService);
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void uploadInitialDocumentsRejectsUserWithoutProjectPermission() {
        doThrow(new AccessDeniedException("Access is denied"))
                .when(projectAuthorizationService)
                .requireProjectPm(PROJECT_ID);

        assertThatThrownBy(() ->
                projectDocumentService.uploadInitialDocuments(PROJECT_ID, List.of(textFile())))
                .isInstanceOf(AccessDeniedException.class);

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void uploadInitialDocumentsDoesNotSaveMetadataWhenS3UploadFails() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("S3 unavailable"));

        assertThatThrownBy(() ->
                projectDocumentService.uploadInitialDocuments(PROJECT_ID, List.of(textFile())))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(exception.getCode()).isEqualTo("DOCUMENT_STORAGE_ERROR");
                });

        verifyNoInteractions(projectDocumentRepository);
    }

    @Test
    void uploadInitialDocumentsDeletesS3ObjectWhenDatabaseSaveFails() {
        doThrow(new DataIntegrityViolationException("database unavailable"))
                .when(projectDocumentRepository)
                .saveAllAndFlush(any());

        assertThatThrownBy(() ->
                projectDocumentService.uploadInitialDocuments(PROJECT_ID, List.of(textFile())))
                .isInstanceOf(DataIntegrityViolationException.class);

        ArgumentCaptor<PutObjectRequest> putCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<DeleteObjectRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).putObject(putCaptor.capture(), any(RequestBody.class));
        verify(s3Client).deleteObject(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(deleteCaptor.getValue().key()).isEqualTo(putCaptor.getValue().key());
    }

    @Test
    void uploadInitialDocumentsValidatesEveryFileBeforeS3Upload() {
        MockMultipartFile validFile = textFile();
        MockMultipartFile invalidFile =
                new MockMultipartFile("files", "empty.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() ->
                projectDocumentService.uploadInitialDocuments(PROJECT_ID, List.of(validFile, invalidFile)))
                .isInstanceOf(ApiException.class)
                .hasMessage("invalid file");

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    private MockMultipartFile textFile() {
        return new MockMultipartFile(
                "files",
                "requirements.txt",
                "text/plain",
                "hello".getBytes()
        );
    }
}
