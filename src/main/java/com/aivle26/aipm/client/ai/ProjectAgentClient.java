package com.aivle26.aipm.client.ai;

import com.aivle26.aipm.Dto.project.AgentRequestResult;



public interface ProjectAgentClient {
    // 프로젝트 ID로 저장 문서 분석을 요청하고 AI 작업 접수 결과를 반환한다.
    AgentRequestResult requestDocumentAnalysis(Long projectId);

    // 프로젝트 ID로 WBS 생성을 요청하고 AI 작업 접수 결과를 반환한다.
    AgentRequestResult requestWbsGeneration(Long projectId);

    // 프로젝트 ID로 일정 생성을 요청하고 AI 작업 접수 결과를 반환한다.
    AgentRequestResult requestScheduleGeneration(Long projectId);
}
