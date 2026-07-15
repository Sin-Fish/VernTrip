package com.vernflow.modules.admin.repository;

import com.vernflow.modules.admin.domain.AdminOperationLog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminOperationLogRepository extends JpaRepository<AdminOperationLog, Long> {

    Page<AdminOperationLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<AdminOperationLog> findTop10ByOrderByCreatedAtDesc();
}
