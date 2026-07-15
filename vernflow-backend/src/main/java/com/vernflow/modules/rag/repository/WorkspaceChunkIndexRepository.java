package com.vernflow.modules.rag.repository;

import com.vernflow.modules.rag.domain.WorkspaceChunkIndex;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkspaceChunkIndexRepository extends JpaRepository<WorkspaceChunkIndex, Long> {

    List<WorkspaceChunkIndex> findAllByDocumentIdOrderBySortIndexAsc(Long documentId);

    void deleteByDocumentId(Long documentId);
}
