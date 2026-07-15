package com.vernflow.modules.paper.repository;

import com.vernflow.modules.paper.domain.*;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QaSessionRepository extends JpaRepository<QaSession, Long> {

    @EntityGraph(attributePaths = {"paper"})
    List<QaSession> findAllByOrderByUpdatedAtDesc();

    @EntityGraph(attributePaths = {"paper"})
    List<QaSession> findAllByPaperIdOrderByUpdatedAtDesc(Long paperId);

    @EntityGraph(attributePaths = {"paper", "evidences", "evidences.paper", "evidences.chunk"})
    Optional<QaSession> findWithEvidencesById(Long id);
}

