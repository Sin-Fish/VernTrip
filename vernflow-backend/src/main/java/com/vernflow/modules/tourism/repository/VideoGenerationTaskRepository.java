package com.vernflow.modules.tourism.repository;

import com.vernflow.modules.tourism.domain.VideoGenerationTask;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface VideoGenerationTaskRepository extends JpaRepository<VideoGenerationTask, Long> {

    List<VideoGenerationTask> findTop50ByStatusOrderByCreatedAtAsc(VideoGenerationTask.Status status);

    long countByStatus(VideoGenerationTask.Status status);

    List<VideoGenerationTask> findByResourceIdInAndStatusOrderByUpdatedAtDesc(
            Collection<Long> resourceIds,
            VideoGenerationTask.Status status
    );

    List<VideoGenerationTask> findTop20ByCreatedByOrderByCreatedAtDesc(Long createdBy);
}
