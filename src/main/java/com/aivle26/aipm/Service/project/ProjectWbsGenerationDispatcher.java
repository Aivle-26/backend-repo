package com.aivle26.aipm.Service.project;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class ProjectWbsGenerationDispatcher {
    private final ProjectWbsGenerationWorker worker;
    private final ProjectWbsGenerationService generationService;
    private final TaskExecutor taskExecutor;

    public ProjectWbsGenerationDispatcher(
            ProjectWbsGenerationWorker worker,
            ProjectWbsGenerationService generationService,
            @Qualifier("wbsGenerationExecutor") TaskExecutor taskExecutor
    ) {
        this.worker = worker;
        this.generationService = generationService;
        this.taskExecutor = taskExecutor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(WbsGenerationRequestedEvent event) {
        try {
            taskExecutor.execute(() -> worker.run(event.generationId(), event.projectId()));
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to enqueue WBS generation. generationId={}, projectId={}",
                    event.generationId(),
                    event.projectId(),
                    exception
            );
            generationService.markFailed(
                    event.generationId(),
                    "WBS_GENERATION_QUEUE_UNAVAILABLE",
                    "WBS 생성 작업을 실행 대기열에 등록하지 못했습니다."
            );
        }
    }
}
