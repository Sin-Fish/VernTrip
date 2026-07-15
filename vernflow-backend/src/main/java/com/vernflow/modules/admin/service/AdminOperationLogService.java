package com.vernflow.modules.admin.service;

import com.vernflow.modules.admin.domain.AdminOperationLog;
import com.vernflow.modules.admin.repository.AdminOperationLogRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminOperationLogService {

    private final AdminOperationLogRepository adminOperationLogRepository;

    public AdminOperationLogService(AdminOperationLogRepository adminOperationLogRepository) {
        this.adminOperationLogRepository = adminOperationLogRepository;
    }

    @Transactional
    public void log(String actor, String action, String targetType, String targetPath, String detail) {
        adminOperationLogRepository.save(AdminOperationLog.create(
                actor == null || actor.isBlank() ? "admin" : actor,
                action,
                targetType,
                targetPath,
                detail
        ));
    }
}
