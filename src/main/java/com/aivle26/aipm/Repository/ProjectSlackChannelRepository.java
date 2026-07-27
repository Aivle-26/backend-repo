package com.aivle26.aipm.Repository;

import com.aivle26.aipm.Entity.ProjectSlackChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectSlackChannelRepository extends JpaRepository<ProjectSlackChannel, Long> {

    List<ProjectSlackChannel> findByProjectId(Long projectId);

    Optional<ProjectSlackChannel> findByProjectIdAndChannelId(Long projectId, String channelId);
}
