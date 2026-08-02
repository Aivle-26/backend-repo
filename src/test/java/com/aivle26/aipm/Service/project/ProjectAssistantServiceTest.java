package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.DeliverableRagRequest;
import com.aivle26.aipm.Dto.project.ProjectAssistantQueryRequest;
import com.aivle26.aipm.Dto.project.ProjectAssistantQueryResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectRequirement;
import com.aivle26.aipm.Entity.project.ProjectStatus;
import com.aivle26.aipm.Entity.project.RequirementPriority;
import com.aivle26.aipm.Entity.project.RequirementStatus;
import com.aivle26.aipm.Entity.project.RequirementType;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementRepository;
import com.aivle26.aipm.Repository.project.ProjectScheduleRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsTaskRepository;
import com.aivle26.aipm.client.ai.ProjectAssistantClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectAssistantServiceTest {

    @Mock ProjectAuthorizationService authorizationService;
    @Mock ProjectRepository projectRepository;
    @Mock ProjectRequirementRepository requirementRepository;
    @Mock ProjectWbsTaskRepository wbsTaskRepository;
    @Mock ProjectScheduleRepository scheduleRepository;
    @Mock ProjectAssistantClient assistantClient;
    @InjectMocks ProjectAssistantService service;

    @Test
    void checksAccessAndBuildsProjectKnowledgeForAi() {
        Project project = new Project();
        project.setId(1L);
        project.setName("AIPM");
        project.setStatus(ProjectStatus.ACTIVE);

        ProjectRequirement requirement = new ProjectRequirement();
        requirement.setId(10L);
        requirement.setTitle("로그인");
        requirement.setDescription("SSO 로그인을 지원한다.");
        requirement.setType(RequirementType.FUNCTIONAL);
        requirement.setPriority(RequirementPriority.HIGH);
        requirement.setStatus(RequirementStatus.CONFIRMED);
        requirement.setIncludedInFinal(true);
        requirement.setConfirmed(true);

        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(requirementRepository.findByProjectIdOrderByIdAsc(1L)).thenReturn(List.of(requirement));
        when(wbsTaskRepository.findByProjectIdOrderByOrderIndexAscIdAsc(1L)).thenReturn(List.of());
        when(scheduleRepository.findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(1L)).thenReturn(List.of());
        var aiResponse = new ProjectAssistantQueryResponse(1L, "답변", List.of(), OffsetDateTime.now(), "SUCCEEDED");
        when(assistantClient.query(org.mockito.ArgumentMatchers.any())).thenReturn(aiResponse);

        var result = service.query(1L, new ProjectAssistantQueryRequest(" 로그인 조건은? ", null));

        verify(authorizationService).requireProjectAccess(1L);
        ArgumentCaptor<DeliverableRagRequest> captor = ArgumentCaptor.forClass(DeliverableRagRequest.class);
        verify(assistantClient).query(captor.capture());
        assertThat(captor.getValue().question()).isEqualTo("로그인 조건은?");
        assertThat(captor.getValue().enableLlm()).isTrue();
        assertThat(captor.getValue().deliverableDocuments()).hasSize(2);
        assertThat(captor.getValue().deliverableDocuments().get(1).requirementId()).isEqualTo(10L);
        assertThat(result).isSameAs(aiResponse);
    }
}
