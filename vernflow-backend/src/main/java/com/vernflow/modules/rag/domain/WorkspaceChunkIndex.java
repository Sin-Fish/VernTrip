package com.vernflow.modules.rag.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "workspace_chunk_index")
public class WorkspaceChunkIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private WorkspaceDocumentIndex document;

    @Column(name = "sort_index", nullable = false)
    private int sortIndex;

    @Column(name = "chunk_text", nullable = false, columnDefinition = "TEXT")
    private String chunkText;

    @Column(name = "embedding_id", length = 120)
    private String embeddingId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected WorkspaceChunkIndex() {
    }

    private WorkspaceChunkIndex(
            WorkspaceDocumentIndex document,
            int sortIndex,
            String chunkText
    ) {
        LocalDateTime now = LocalDateTime.now();
        this.document = document;
        this.sortIndex = sortIndex;
        this.chunkText = chunkText;
        this.embeddingId = null;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static WorkspaceChunkIndex create(
            WorkspaceDocumentIndex document,
            int sortIndex,
            String chunkText
    ) {
        return new WorkspaceChunkIndex(document, sortIndex, chunkText);
    }

    public Long getId() {
        return id;
    }

    public WorkspaceDocumentIndex getDocument() {
        return document;
    }

    public int getSortIndex() {
        return sortIndex;
    }

    public String getChunkText() {
        return chunkText;
    }

    public String getEmbeddingId() {
        return embeddingId;
    }

    public void syncEmbedding(String embeddingId) {
        this.embeddingId = embeddingId;
        this.updatedAt = LocalDateTime.now();
    }
}
