package com.aivle26.aipm.Service.user;

import com.aivle26.aipm.Dto.user.SaveUserCapabilitiesRequest;
import com.aivle26.aipm.Dto.user.UserCapabilitiesResponse;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Entity.user.UserCapabilityProfile;
import com.aivle26.aipm.Entity.user.UserSkill;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.user.UserCapabilityProfileRepository;
import com.aivle26.aipm.Repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserCapabilityService {

    private final UserRepository userRepository;
    private final UserCapabilityProfileRepository capabilityProfileRepository;

    @Transactional
    public UserCapabilitiesResponse save(String employeeNumber, SaveUserCapabilitiesRequest request) {
        User user = userRepository.findById(employeeNumber)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "user not found"));
        List<String> roles = normalizeUnique(request.roles(), "duplicate role code");

        Set<String> skillCodes = new LinkedHashSet<>();
        List<UserSkill> skills = new ArrayList<>();
        for (SaveUserCapabilitiesRequest.Skill requestedSkill : request.skills()) {
            String skillCode = normalizeCode(requestedSkill.skillCode());
            if (!skillCodes.add(skillCode)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "duplicate skill code");
            }
            UserSkill skill = new UserSkill();
            skill.setSkillCode(skillCode);
            skill.setProficiencyLevel(requestedSkill.proficiencyLevel());
            skill.setExperienceMonths(requestedSkill.experienceMonths());
            skills.add(skill);
        }

        UserCapabilityProfile profile = capabilityProfileRepository.findById(employeeNumber)
                .orElseGet(UserCapabilityProfile::new);
        profile.setUser(user);
        profile.setRoles(new LinkedHashSet<>(roles));
        profile.setSkills(skills);
        return toResponse(capabilityProfileRepository.save(profile));
    }

    private List<String> normalizeUnique(List<String> values, String duplicateMessage) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (!normalized.add(normalizeCode(value))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, duplicateMessage);
            }
        }
        return List.copyOf(normalized);
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private UserCapabilitiesResponse toResponse(UserCapabilityProfile profile) {
        return new UserCapabilitiesResponse(
                profile.getEmployeeNumber(),
                List.copyOf(profile.getRoles()),
                profile.getSkills().stream()
                        .map(skill -> new UserCapabilitiesResponse.Skill(
                                skill.getSkillCode(),
                                skill.getProficiencyLevel(),
                                skill.getExperienceMonths()
                        ))
                        .toList()
        );
    }
}
