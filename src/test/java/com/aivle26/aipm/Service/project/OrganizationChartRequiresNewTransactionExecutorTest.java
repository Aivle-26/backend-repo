package com.aivle26.aipm.Service.project;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(OrganizationChartRequiresNewTransactionExecutor.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrganizationChartRequiresNewTransactionExecutorTest {

    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private OrganizationChartRequiresNewTransactionExecutor executor;
    @Autowired private EntityManager entityManager;

    @Test
    void afterCommitCallbackCanRunPessimisticQueryInNewTransaction() {
        AtomicBoolean queryCompleted = new AtomicBoolean();
        TransactionTemplate originalWrite = new TransactionTemplate(transactionManager);

        originalWrite.executeWithoutResult(status ->
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                executor.execute(() -> {
                                    entityManager.createNativeQuery("select 1 for update")
                                            .getSingleResult();
                                    queryCompleted.set(true);
                                    return null;
                                });
                            }
                        }
                )
        );

        assertThat(queryCompleted).isTrue();
    }
}
