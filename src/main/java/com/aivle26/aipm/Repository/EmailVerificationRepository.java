package com.aivle26.aipm.Repository;

import com.aivle26.aipm.Entity.EmailVerification;
import com.aivle26.aipm.Entity.VerificationPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {
    Optional<EmailVerification> findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(
            String email,
            VerificationPurpose purpose
    );

    Optional<EmailVerification> findTopByEmployeeNumberAndPurposeOrderByCreatedAtDesc(
            String employeeNumber,
            VerificationPurpose purpose
    );

    List<EmailVerification> findByEmailIgnoreCaseAndPurposeAndUsedFalse(
            String email,
            VerificationPurpose purpose
    );

    List<EmailVerification> findByEmployeeNumberAndPurposeAndUsedFalse(
            String employeeNumber,
            VerificationPurpose purpose
    );
}
