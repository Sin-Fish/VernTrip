package com.vernflow.modules.openclaw.repository;

import com.vernflow.modules.openclaw.domain.OpenClawSessionContext;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OpenClawSessionContextRepository extends JpaRepository<OpenClawSessionContext, Long> {

    Optional<OpenClawSessionContext> findBySessionKey(String sessionKey);

    Optional<OpenClawSessionContext> findFirstByUserIdAndAssistantSessionIdOrderByUpdatedAtDesc(
            Long userId,
            Long assistantSessionId
    );
}
