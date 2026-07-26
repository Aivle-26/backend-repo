package com.aivle26.aipm.Repository.user;

import com.aivle26.aipm.Entity.user.EmailVerification;
import com.aivle26.aipm.Entity.user.VerificationPurpose;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {
    // 이메일과 인증 목적에 해당하는 가장 최근 인증 레코드를 조회한다.
    Optional<EmailVerification> findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(
            String email,
            VerificationPurpose purpose
    );

    // 사번과 인증 목적에 해당하는 가장 최근 인증 레코드를 조회한다.
    Optional<EmailVerification> findTopByEmployeeNumberAndPurposeOrderByCreatedAtDesc(
            String employeeNumber,
            VerificationPurpose purpose
    );

    // 이메일과 목적이 일치하는 미사용 인증 레코드를 모두 조회한다.
    List<EmailVerification> findByEmailIgnoreCaseAndPurposeAndUsedFalse(
            String email,
            VerificationPurpose purpose
    );

    // 사번과 목적이 일치하는 미사용 인증 레코드를 모두 조회한다.
    List<EmailVerification> findByEmployeeNumberAndPurposeAndUsedFalse(
            String employeeNumber,
            VerificationPurpose purpose
    );
}
