package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.AdminCreateUserRequest;
import com.aivle26.aipm.Dto.UserResponse;
import com.aivle26.aipm.Entity.User;
import com.aivle26.aipm.Entity.UserStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserService {
    private static final String INITIAL_PASSWORD = "PMagent123!";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse createUser(AdminCreateUserRequest request) {
        String employeeNumber = request.employeeNumber().trim();
        if (userRepository.existsById(employeeNumber)) {
            throw new ApiException(HttpStatus.CONFLICT, "employeeNumber already exists");
        }

        User user = new User();
        user.setEmployeeNumber(employeeNumber);
        user.setName(request.name().trim());
        user.setRole(request.role().trim());
        user.setPassword(passwordEncoder.encode(INITIAL_PASSWORD));
        user.setStatus(UserStatus.MUST_CHANGE_PASSWORD);
        user.setEmailVerified(false);

        User savedUser = userRepository.save(user);
        return new UserResponse(
                savedUser.getEmployeeNumber(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getRole(),
                savedUser.getStatus()
        );
    }
}
