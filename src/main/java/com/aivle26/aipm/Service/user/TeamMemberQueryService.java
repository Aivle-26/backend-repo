package com.aivle26.aipm.Service.user;

import com.aivle26.aipm.Dto.user.TeamMemberResponse;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Entity.user.UserCapabilityProfile;
import com.aivle26.aipm.Entity.user.UserStatus;
import com.aivle26.aipm.Repository.user.UserCapabilityProfileRepository;
import com.aivle26.aipm.Repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamMemberQueryService {

    private static final String TEAM_MEMBER_ACCOUNT_ROLE = "STAFF";

    private final UserRepository userRepository;
    private final UserCapabilityProfileRepository capabilityProfileRepository;

    @Transactional(readOnly = true)
    public List<TeamMemberResponse> getAllActiveTeamMembers() {
        List<User> members = userRepository.findAllByRoleAndStatusOrderByNameAscEmployeeNumberAsc(
                TEAM_MEMBER_ACCOUNT_ROLE,
                UserStatus.ACTIVE
        );
        if (members.isEmpty()) {
            return List.of();
        }

        List<String> employeeNumbers = members.stream()
                .map(User::getEmployeeNumber)
                .toList();
        Map<String, UserCapabilityProfile> profilesByEmployeeNumber = capabilityProfileRepository
                .findAllByEmployeeNumberIn(employeeNumbers)
                .stream()
                .collect(Collectors.toMap(
                        UserCapabilityProfile::getEmployeeNumber,
                        Function.identity()
                ));

        return members.stream()
                .map(member -> toResponse(
                        member,
                        profilesByEmployeeNumber.get(member.getEmployeeNumber())
                ))
                .toList();
    }

    private TeamMemberResponse toResponse(User member, UserCapabilityProfile profile) {
        if (profile == null) {
            return new TeamMemberResponse(
                    member.getEmployeeNumber(),
                    member.getName(),
                    member.getEmail(),
                    false,
                    List.of(),
                    List.of()
            );
        }

        // 담당자 추천은 "역할이 1개 이상 등록된" 프로필만 후보로 인정한다
        // (PlanningResourceContextAssembler.loadProfiles 참고).
        // 여기서 프로필 존재만으로 등록됨 처리하면 화면엔 "등록됨"인데 추천 후보에선
        // 조용히 빠지는 불일치가 생기므로, 판정 기준을 동일하게 맞춘다.
        List<String> roles = profile.getRoles().stream().sorted().toList();
        boolean capabilityRegistered = !roles.isEmpty();
        List<TeamMemberResponse.Skill> skills = profile.getSkills().stream()
                .map(skill -> new TeamMemberResponse.Skill(
                        skill.getSkillCode(),
                        skill.getProficiencyLevel(),
                        skill.getExperienceMonths()
                ))
                .toList();
        return new TeamMemberResponse(
                member.getEmployeeNumber(),
                member.getName(),
                member.getEmail(),
                capabilityRegistered,
                roles,
                skills
        );
    }
}
