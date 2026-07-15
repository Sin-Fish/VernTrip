package com.vernflow.modules.assistant.repository;

import com.vernflow.modules.assistant.domain.AssistantSession;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssistantSessionRepository extends JpaRepository<AssistantSession, Long> {

    List<AssistantSession> findAllByOrderByUpdatedAtDesc();

    Page<AssistantSession> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    List<AssistantSession> findAllByUserIdOrderByUpdatedAtDesc(Long userId);

    Page<AssistantSession> findAllByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    java.util.Optional<AssistantSession> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);
}
