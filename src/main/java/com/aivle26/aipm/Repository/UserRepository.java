package com.aivle26.aipm.Repository;

import com.aivle26.aipm.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailAndEmployeeNumberNot(String email, String employeeNumber);

    boolean existsByResetToken(String resetToken);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByEmployeeNumber(String employeeNumber);

    Optional<User> findByEmployeeNumberAndEmail(String employeeNumber, String email);

    Optional<User> findByResetToken(String resetToken);
}
