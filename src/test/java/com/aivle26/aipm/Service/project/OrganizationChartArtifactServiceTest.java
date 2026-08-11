package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Config.storage.DocumentObjectStorage;
import com.aivle26.aipm.Dto.project.OrganizationChartGenerateResponse;
import com.aivle26.aipm.Dto.project.OrganizationChartHierarchyUpdateRequest;
import com.aivle26.aipm.Dto.project.OrganizationChartRenderRequest;
import com.aivle26.aipm.Dto.project.PlanningResourceRecommendRequest;
import com.aivle26.aipm.Entity.ArtifactApprovalStatus;
import com.aivle26.aipm.Entity.ProjectArtifact;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectDocument;
import com.aivle26.aipm.Entity.project.ProjectMember;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Repository.ProjectArtifactRepository;
import com.aivle26.aipm.Repository.project.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.project.ProjectMemberRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Service.ArtifactVersionComparator;
import com.aivle26.aipm.client.ai.GeneratedOrganizationChart;
import com.aivle26.aipm.client.ai.PlanningResourceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrganizationChartArtifactServiceTest {

    private static final byte[] JPEG = {
            (byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01, 0x02
    };

    @Mock private ProjectAuthorizationService projectAuthorizationService;
    @Mock private PlanningResourceContextAssembler contextAssembler;
    @Mock private PlanningResourceClient planningResourceClient;
    @Mock private OrganizationChartArtifactPolicy artifactPolicy;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private ProjectDocumentRepository documentRepository;
    @Mock private ProjectArtifactRepository artifactRepository;
    @Mock private DocumentObjectStorage documentObjectStorage;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private TransactionStatus transactionStatus;

    private final Map<String, byte[]> storedObjects = new HashMap<>();
    private final List<ProjectArtifact> artifacts = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(10L);
    private OrganizationChartArtifactService service;
    private Project project;

    @BeforeEach
    void setUp() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new OrganizationChartArtifactService(
                projectAuthorizationService,
                contextAssembler,
                planningResourceClient,
                artifactPolicy,
                projectRepository,
                projectMemberRepository,
                documentRepository,
                artifactRepository,
                documentObjectStorage,
                new ArtifactVersionComparator(),
                transactionTemplate,
                objectMapper
        );
        project = new Project();
        project.setId(1L);
        project.setName("Organization Project");
        project.setPm(user("PM001", "Kim PM"));

        when(projectRepository.findForUpdate(1L)).thenReturn(Optional.of(project));
        when(artifactRepository.findByProjectIdAndArtifactType(
                1L,
                OrganizationChartArtifactPolicy.TYPE
        )).thenAnswer(ignored -> List.copyOf(artifacts));
        when(documentRepository.saveAndFlush(any(ProjectDocument.class)))
                .thenAnswer(invocation -> {
                    ProjectDocument document = invocation.getArgument(0);
                    document.setId(ids.incrementAndGet());
                    document.onCreate();
                    return document;
                });
        when(artifactRepository.saveAndFlush(any(ProjectArtifact.class)))
                .thenAnswer(invocation -> {
                    ProjectArtifact artifact = invocation.getArgument(0);
                    artifact.setId(ids.incrementAndGet());
                    artifact.onCreate();
                    artifacts.add(artifact);
                    return artifact;
                });
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        });
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            InputStream input = invocation.getArgument(3);
            storedObjects.put(key, input.readAllBytes());
            return null;
        }).when(documentObjectStorage).put(any(), any(), anyLong(), any());
        when(documentObjectStorage.get(any())).thenAnswer(invocation -> {
            byte[] value = storedObjects.get(invocation.<String>getArgument(0));
            if (value == null) {
                throw new IllegalStateException("missing object");
            }
            return value;
        });
        doAnswer(invocation -> {
            storedObjects.remove(invocation.<String>getArgument(0));
            return null;
        }).when(documentObjectStorage).delete(any());
    }

    @Test
    void generationStoresJsonAndJpegAsVersionOne() {
        arrangeGeneration();

        var response = service.generate(1L);

        assertThat(response.version()).isEqualTo("1.0");
        assertThat(response.approvalStatus()).isEqualTo(ArtifactApprovalStatus.PENDING);
        assertThat(storedObjects.keySet()).hasSize(2);
        assertThat(storedObjects.keySet()).anyMatch(key -> key.endsWith(".json"));
        assertThat(storedObjects.keySet()).anyMatch(key -> key.endsWith(".jpg"));
        assertThat(storedObjects.values()).anyMatch(value -> value[0] == (byte) 0xff);
    }

    @Test
    void automaticGenerationDoesNotCreateAnotherArtifactAfterConcurrentInitialCreation() {
        arrangeGeneration();
        service.generate(1L);
        int storedObjectCount = storedObjects.size();

        var response = service.generateInitialAutomatically(1L);

        assertThat(response.version()).isEqualTo("1.0");
        assertThat(artifacts).hasSize(1);
        assertThat(storedObjects).hasSize(storedObjectCount);
    }

    @Test
    void latestStructureUsesStableMemberIdentifiersAndKeepsMissingCapability() {
        arrangeGeneration();
        service.generate(1L);

        var structure = service.getLatestStructure(1L);

        assertThat(structure.projectManagerMemberId()).isEqualTo("PM001");
        assertThat(structure.members()).extracting(member -> member.memberId())
                .containsExactly("PM001", "DEV002", "DEV003");
        assertThat(structure.members().get(0).parentMemberId()).isNull();
        assertThat(structure.members().get(1).parentMemberId()).isEqualTo("PM001");
        assertThat(structure.members().get(2).parentMemberId()).isEqualTo("DEV002");
        assertThat(structure.members().get(2).capabilityRegistered()).isFalse();
        assertThat(structure.members().get(2).projectJobFamily()).isNull();
    }

    @Test
    void hierarchyMoveCreatesNextVersionWithoutChangingRolesOrAssignments() {
        arrangeGeneration();
        service.generate(1L);
        arrangeActiveMembers();
        when(projectRepository.findWithPmById(1L)).thenReturn(Optional.of(project));
        when(planningResourceClient.renderOrganizationChart(any()))
                .thenAnswer(invocation -> rendered(invocation
                        .<OrganizationChartRenderRequest>getArgument(0).organization()));

        var response = service.updateHierarchy(
                1L,
                new OrganizationChartHierarchyUpdateRequest(
                        "1.0",
                        List.of(
                                hierarchy("PM001", null, 0),
                                hierarchy("DEV002", "PM001", 0),
                                hierarchy("DEV003", "PM001", 1)
                        )
                )
        );

        assertThat(response.version()).isEqualTo("1.1");
        assertThat(artifacts).hasSize(2);
        assertThat(artifacts.get(1).getDocument().getProcessingMode())
                .isEqualTo("MANUAL_HIERARCHY_EDIT");
        assertThat(storedObjects.keySet()).hasSize(4);
        var structure = service.getLatestStructure(1L);
        assertThat(structure.members()).filteredOn(member -> member.memberId().equals("DEV003"))
                .singleElement()
                .extracting(member -> member.parentMemberId())
                .isEqualTo("PM001");

        org.mockito.ArgumentCaptor<OrganizationChartRenderRequest> captor =
                org.mockito.ArgumentCaptor.forClass(OrganizationChartRenderRequest.class);
        verify(planningResourceClient).renderOrganizationChart(captor.capture());
        var movedTeam = captor.getValue().organization().teams().stream()
                .filter(team -> team.memberIds().equals(List.of(3L)))
                .findFirst()
                .orElseThrow();
        assertThat(movedTeam.primaryRoles()).isEmpty();
        assertThat(movedTeam.assignedWbsIds()).isEmpty();
        assertThat(movedTeam.reportsTo()).isEqualTo("member:1:1");

        var secondResponse = service.updateHierarchy(
                1L,
                new OrganizationChartHierarchyUpdateRequest(
                        "1.1",
                        List.of(
                                hierarchy("PM001", null, 0),
                                hierarchy("DEV002", "PM001", 0),
                                hierarchy("DEV003", "DEV002", 0)
                        )
                )
        );
        assertThat(secondResponse.version()).isEqualTo("1.2");
        assertThat(artifacts).hasSize(3);
        assertThat(storedObjects).hasSize(6);
    }

    @Test
    void rejectsCycleBeforeRendererOrStorage() {
        arrangeGeneration();
        service.generate(1L);
        arrangeActiveMembers();
        when(projectRepository.findWithPmById(1L)).thenReturn(Optional.of(project));
        int objectCount = storedObjects.size();

        assertThatThrownBy(() -> service.updateHierarchy(
                1L,
                new OrganizationChartHierarchyUpdateRequest(
                        "1.0",
                        List.of(
                                hierarchy("PM001", null, 0),
                                hierarchy("DEV002", "DEV003", 0),
                                hierarchy("DEV003", "DEV002", 0)
                        )
                )
        )).hasMessageContaining("cycle");

        verify(planningResourceClient, never()).renderOrganizationChart(any());
        assertThat(storedObjects).hasSize(objectCount);
    }

    @Test
    void rejectsMovingProjectManagerBelowAnotherMember() {
        arrangeGeneration();
        service.generate(1L);
        arrangeActiveMembers();
        when(projectRepository.findWithPmById(1L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.updateHierarchy(
                1L,
                new OrganizationChartHierarchyUpdateRequest(
                        "1.0",
                        List.of(
                                hierarchy("PM001", "DEV002", 0),
                                hierarchy("DEV002", null, 0),
                                hierarchy("DEV003", "DEV002", 0)
                        )
                )
        )).hasMessageContaining("project manager");

        verify(planningResourceClient, never()).renderOrganizationChart(any());
    }

    @Test
    void rejectsDuplicateUnknownParentAndChangedActiveMembership() {
        arrangeGeneration();
        service.generate(1L);
        arrangeActiveMembers();
        when(projectRepository.findWithPmById(1L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.updateHierarchy(
                1L,
                new OrganizationChartHierarchyUpdateRequest(
                        "1.0",
                        List.of(
                                hierarchy("PM001", null, 0),
                                hierarchy("DEV002", "PM001", 0),
                                hierarchy("DEV002", "PM001", 1),
                                hierarchy("DEV003", "DEV002", 0)
                        )
                )
        )).hasMessageContaining("more than once");

        assertThatThrownBy(() -> service.updateHierarchy(
                1L,
                new OrganizationChartHierarchyUpdateRequest(
                        "1.0",
                        List.of(
                                hierarchy("PM001", null, 0),
                                hierarchy("DEV002", "MISSING", 0),
                                hierarchy("DEV003", "DEV002", 0)
                        )
                )
        )).hasMessageContaining("parent");

        when(projectMemberRepository
                .findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(1L))
                .thenReturn(List.of(
                        projectMember(project.getPm()),
                        projectMember(user("DEV002", "Lee Lead"))
                ));
        assertThatThrownBy(() -> service.updateHierarchy(
                1L,
                new OrganizationChartHierarchyUpdateRequest(
                        "1.0",
                        List.of(
                                hierarchy("PM001", null, 0),
                                hierarchy("DEV002", "PM001", 0),
                                hierarchy("DEV003", "DEV002", 0)
                        )
                )
        )).hasMessageContaining("Active project members changed");

        verify(planningResourceClient, never()).renderOrganizationChart(any());
    }

    @Test
    void databaseFailureCleansBothNewObjects() {
        arrangeGeneration();
        doThrow(new IllegalStateException("database unavailable"))
                .when(documentRepository)
                .saveAndFlush(any(ProjectDocument.class));

        assertThatThrownBy(() -> service.generate(1L))
                .isInstanceOf(IllegalStateException.class);

        assertThat(storedObjects).isEmpty();
        verify(documentObjectStorage, org.mockito.Mockito.times(2)).delete(any());
    }

    @Test
    void partialStateUploadFailureCleansBothObjectKeys() throws Exception {
        arrangeGeneration();
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            InputStream input = invocation.getArgument(3);
            storedObjects.put(key, input.readAllBytes());
            throw new IllegalStateException("upload interrupted");
        }).when(documentObjectStorage).put(any(), any(), anyLong(), any());

        assertThatThrownBy(() -> service.generate(1L))
                .hasMessageContaining("could not be uploaded");

        assertThat(storedObjects).isEmpty();
        assertThat(artifacts).isEmpty();
        verify(documentObjectStorage, org.mockito.Mockito.times(2)).delete(any());
    }

    @Test
    void manualSaveFailureKeepsPreviousVersionAndCleansNewObjects() {
        arrangeGeneration();
        service.generate(1L);
        arrangeActiveMembers();
        when(projectRepository.findWithPmById(1L)).thenReturn(Optional.of(project));
        when(planningResourceClient.renderOrganizationChart(any()))
                .thenAnswer(invocation -> rendered(invocation
                        .<OrganizationChartRenderRequest>getArgument(0).organization()));
        doThrow(new IllegalStateException("database unavailable"))
                .when(documentRepository)
                .saveAndFlush(any(ProjectDocument.class));

        assertThatThrownBy(() -> service.updateHierarchy(
                1L,
                new OrganizationChartHierarchyUpdateRequest(
                        "1.0",
                        List.of(
                                hierarchy("PM001", null, 0),
                                hierarchy("DEV002", "PM001", 0),
                                hierarchy("DEV003", "PM001", 1)
                        )
                )
        )).isInstanceOf(IllegalStateException.class);

        assertThat(artifacts).hasSize(1);
        assertThat(artifacts.get(0).getVersion()).isEqualTo("1.0");
        assertThat(storedObjects).hasSize(2);
        verify(documentObjectStorage, org.mockito.Mockito.times(2)).delete(any());
    }

    @Test
    void downloadRemainsAccessControlledAndReadsOnlyLatestJpeg() {
        arrangeGeneration();
        service.generate(1L);

        var content = service.downloadLatest(1L);

        assertThat(content.contentType()).isEqualTo("image/jpeg");
        assertThat(content.content()).containsExactly(JPEG);

        doThrow(new AccessDeniedException("Access is denied"))
                .when(projectAuthorizationService)
                .requireProjectAccess(1L);
        assertThatThrownBy(() -> service.downloadLatest(1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    private void arrangeGeneration() {
        User pm = project.getPm();
        User lead = user("DEV002", "Lee Lead");
        User unknown = user("DEV003", "Park Member");
        var window = new PlanningResourceContextAssembler.DateWindow(
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-31")
        );
        Map<Long, PlanningResourceContextAssembler.CandidateContext> candidates =
                new LinkedHashMap<>();
        candidates.put(1L, new PlanningResourceContextAssembler.CandidateContext(
                1L, pm, null, 32.0, window
        ));
        candidates.put(2L, new PlanningResourceContextAssembler.CandidateContext(
                2L, lead, null, 32.0, window
        ));
        candidates.put(3L, new PlanningResourceContextAssembler.CandidateContext(
                3L, unknown, null, 32.0, window
        ));
        PlanningResourceRecommendRequest request = new PlanningResourceRecommendRequest(
                1L,
                "Organization Project",
                List.of(),
                List.of(
                        aiMember(1L, "PM", List.of("PM")),
                        aiMember(2L, "Lead", List.of("TECH_LEAD")),
                        aiMember(3L, "Member", List.of())
                )
        );
        var context = new PlanningResourceContextAssembler.PlanningResourceContext(
                project,
                null,
                List.of(),
                candidates,
                request,
                1L,
                List.of()
        );
        when(contextAssembler.assembleForOrganizationChart(1L)).thenReturn(context);
        when(planningResourceClient.generateOrganizationChart(any()))
                .thenReturn(rendered(organization()));
    }

    private void arrangeActiveMembers() {
        when(projectMemberRepository
                .findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(1L))
                .thenReturn(List.of(
                        projectMember(project.getPm()),
                        projectMember(user("DEV002", "Lee Lead")),
                        projectMember(user("DEV003", "Park Member"))
                ));
    }

    private OrganizationChartGenerateResponse.OrganizationView organization() {
        return new OrganizationChartGenerateResponse.OrganizationView(
                1L,
                1L,
                List.of(
                        team("member:1:1", 1L, List.of("PROJECT_MANAGER"), null),
                        team("member:1:2", 2L, List.of("TECH_LEAD"), "member:1:1"),
                        team("member:1:3", 3L, List.of(), "member:1:2")
                ),
                List.of(),
                List.of(),
                List.of("One member has no capability profile."),
                OffsetDateTime.parse("2026-08-05T10:00:00Z")
        );
    }

    private OrganizationChartGenerateResponse.OrganizationTeam team(
            String teamId,
            Long memberId,
            List<String> roles,
            String reportsTo
    ) {
        return new OrganizationChartGenerateResponse.OrganizationTeam(
                teamId,
                "Member " + memberId,
                null,
                List.of(memberId),
                roles,
                List.of(),
                List.of(),
                reportsTo,
                List.of(),
                List.of()
        );
    }

    private GeneratedOrganizationChart rendered(
            OrganizationChartGenerateResponse.OrganizationView organization
    ) {
        return new GeneratedOrganizationChart(
                new OrganizationChartGenerateResponse(
                        organization,
                        "project-1-organization-chart.jpg",
                        "image/jpeg",
                        "ignored-after-client-validation",
                        1400,
                        900
                ),
                JPEG
        );
    }

    private PlanningResourceRecommendRequest.ProjectMember aiMember(
            Long id,
            String name,
            List<String> roles
    ) {
        return new PlanningResourceRecommendRequest.ProjectMember(
                id,
                name,
                roles,
                List.of(),
                List.of()
        );
    }

    private OrganizationChartHierarchyUpdateRequest.MemberHierarchy hierarchy(
            String memberId,
            String parentMemberId,
            int order
    ) {
        return new OrganizationChartHierarchyUpdateRequest.MemberHierarchy(
                memberId,
                parentMemberId,
                order
        );
    }

    private User user(String employeeNumber, String name) {
        User user = new User();
        user.setEmployeeNumber(employeeNumber);
        user.setName(name);
        return user;
    }

    private ProjectMember projectMember(User user) {
        ProjectMember member = new ProjectMember();
        member.setProject(project);
        member.setUser(user);
        member.setActive(true);
        return member;
    }
}
