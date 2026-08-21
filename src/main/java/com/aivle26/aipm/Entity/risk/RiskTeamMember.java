package com.aivle26.aipm.Entity.risk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 담당자 재배정 추천용 더미 팀원.
 *
 * <p>아직 업무배정(assignment) 도메인이 없어서, 재배정 추천에 필요한
 * 사람 데이터(스킬·업무량·지연)를 담을 곳이 없다. 흐름 검증을 위해
 * local 프로필에서만 시드되는 임시 스캐폴드다.
 * 실제 assignment/member 도메인이 생기면 이 엔티티와 사용처는 제거하고
 * 그쪽 데이터로 교체하면 된다. (배정 도메인과 이름 충돌을 피하려 risk 패키지에 둔다)
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "risk_team_members")
public class RiskTeamMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK 대신 단순 컬럼으로 둬서 배정 도메인과 결합하지 않는다. */
    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 100)
    private String memberName;

    @Column(nullable = false, length = 50)
    private String role;

    /** 쉼표 구분 스킬. 더미용 단순 저장. 예: "Java,Spring" */
    @Column(length = 300)
    private String skills;

    /** 0~100 */
    @Column(nullable = false)
    private double workloadRate;

    @Column(nullable = false)
    private int overdueTaskCount;

    /** 이 프로젝트에서 현재 배정 담당자로 볼 더미 플래그. true인 1명이 current_assignee가 된다. */
    @Column(nullable = false)
    private boolean currentAssignee;

    /* ---------- 팀원별 업무 지연 분석(member-delay)용 필드 ---------- */

    @Column(nullable = false)
    private int assignedTaskCount;

    @Column(nullable = false)
    private int completedTaskCount;

    @Column(nullable = false)
    private int inProgressTaskCount;

    /** 평균 지연 일수 */
    @Column(nullable = false)
    private double averageDelayDays;

    /** 마지막 업무 갱신 후 경과 일수 */
    @Column(nullable = false)
    private int daysSinceLastUpdate;
}
