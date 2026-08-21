package com.aivle26.aipm.Service.user;

import com.aivle26.aipm.Dto.user.AdminCreateUserRequest;
import com.aivle26.aipm.Dto.user.UserResponse;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Entity.user.UserStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.user.UserRepository;

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

    // 관리자 요청의 사번·이메일 중복을 검증해 암호화된 사용자 계정을 저장하고 반환한다.
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
