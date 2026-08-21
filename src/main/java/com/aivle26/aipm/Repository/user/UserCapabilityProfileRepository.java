package com.aivle26.aipm.Repository.user;

import com.aivle26.aipm.Entity.user.UserCapabilityProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Collection;
import java.util.List;

public interface UserCapabilityProfileRepository extends JpaRepository<UserCapabilityProfile, String> {
    @EntityGraph(attributePaths = {"roles", "skills"})
    List<UserCapabilityProfile> findAllByEmployeeNumberIn(Collection<String> employeeNumbers);
}
