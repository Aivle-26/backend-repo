package com.aivle26.aipm.Repository.risk;

import com.aivle26.aipm.Entity.risk.RiskTeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** 재배정 추천용 더미 팀원 조회. (assignment 도메인 생기면 제거) */
public interface RiskTeamMemberRepository extends JpaRepository<RiskTeamMember, Long> {
    List<RiskTeamMember> findByProjectId(Long projectId);

    void deleteAllByProjectId(Long projectId);

    @Query("""
            select distinct member.projectId
            from RiskTeamMember member
            where member.memberName = :employeeNumber
              and upper(member.role) = 'STAFF'
            """)
    List<Long> findParticipatingProjectIds(@Param("employeeNumber") String employeeNumber);

    boolean existsByProjectIdAndMemberNameAndRoleIgnoreCase(
            Long projectId,
            String memberName,
            String role
    );
}
