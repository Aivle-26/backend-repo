package com.aivle26.aipm.Service;

import com.aivle26.aipm.Entity.User;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProjectPmResolver {
    private static final String PM_ROLE = "PM";

    private final UserRepository userRepository;

    public User resolve(String pmEmployeeNumber) {
        // TODO: replace request-based PM identification with authenticated principal when auth is introduced.
        return userRepository.findByEmployeeNumber(pmEmployeeNumber.trim())
                .filter(user -> PM_ROLE.equalsIgnoreCase(user.getRole()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "pm user not found"));
    }
}
