package com.aivle26.aipm.Repository;

import com.aivle26.aipm.Entity.CommunicationRiskResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommunicationRiskResultRepository extends JpaRepository<CommunicationRiskResult, Long> {

    Optional<CommunicationRiskResult> findTopByProjectIdOrderByAnalyzedAtDesc(Long projectId);

    void deleteAllByProjectId(Long projectId);
}
