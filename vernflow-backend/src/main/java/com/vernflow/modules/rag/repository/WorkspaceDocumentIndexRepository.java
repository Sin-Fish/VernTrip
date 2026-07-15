package com.vernflow.modules.rag.repository;

import com.vernflow.modules.rag.domain.WorkspaceDocumentIndex;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkspaceDocumentIndexRepository extends JpaRepository<WorkspaceDocumentIndex, Long> {

    Optional<WorkspaceDocumentIndex> findByUserIdAndSourcePath(Long userId, String sourcePath);

    List<WorkspaceDocumentIndex> findAllByUserIdOrderByUpdatedAtDesc(Long userId);
}
