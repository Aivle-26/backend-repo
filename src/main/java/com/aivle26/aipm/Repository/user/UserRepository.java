package com.aivle26.aipm.Repository.user;

import com.aivle26.aipm.Entity.user.User;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    // 대소문자와 무관하게 이메일 중복 여부를 반환한다.
    boolean existsByEmailIgnoreCase(String email);

    // 지정 사번을 제외한 다른 계정의 이메일 중복 여부를 반환한다.
    boolean existsByEmailAndEmployeeNumberNot(String email, String employeeNumber);

    // 대소문자와 무관하게 이메일로 사용자를 조회한다.
    Optional<User> findByEmailIgnoreCase(String email);

    // 사번으로 사용자를 조회한다.
    Optional<User> findByEmployeeNumber(String employeeNumber);

    // 사번과 이메일이 모두 일치하는 사용자를 조회한다.
    Optional<User> findByEmployeeNumberAndEmail(String employeeNumber, String email);

}
