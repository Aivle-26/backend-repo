package com.aivle26.aipm.Entity.user;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "user_capability_profiles")
public class UserCapabilityProfile {

    @Id
    @Column(name = "employee_number", length = 50)
    private String employeeNumber;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_number")
    private User user;

    @ElementCollection
    @CollectionTable(name = "user_professional_roles", joinColumns = @JoinColumn(name = "employee_number"))
    @Column(name = "role_code", nullable = false, length = 100)
    private Set<String> roles = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "user_skills", joinColumns = @JoinColumn(name = "employee_number"))
    @OrderColumn(name = "order_index")
    private List<UserSkill> skills = new ArrayList<>();
}
