package com.vernflow.modules.paper.repository;

import com.vernflow.modules.paper.domain.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface ParseTaskRepository extends JpaRepository<ParseTask, Long> {

    Page<ParseTask> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Optional<ParseTask> findTopByPaperIdOrderByCreatedAtDesc(Long paperId);

    Optional<ParseTask> findTopByStatusOrderByQueueOrderAscCreatedAtAsc(TaskStatus status);

    long countByStatus(TaskStatus status);

    long countByPaperId(Long paperId);

    @Query("select coalesce(min(task.queueOrder), 0) from ParseTask task where task.status in :statuses")
    long findMinQueueOrderByStatuses(@Param("statuses") Collection<TaskStatus> statuses);

    @Query("select coalesce(max(task.queueOrder), 0) from ParseTask task where task.status in :statuses")
    long findMaxQueueOrderByStatuses(@Param("statuses") Collection<TaskStatus> statuses);
}

