package com.vernflow.modules.workbench.repository;

import com.vernflow.modules.workbench.domain.WorkbenchRun;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkbenchRunRepository extends JpaRepository<WorkbenchRun, Long> {

    List<WorkbenchRun> findTop12ByOrderByUpdatedAtDesc();

    Page<WorkbenchRun> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    List<WorkbenchRun> findTop12ByUserIdOrderByUpdatedAtDesc(Long userId);

    Page<WorkbenchRun> findAllByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    java.util.Optional<WorkbenchRun> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);
}
