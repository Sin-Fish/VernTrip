package com.vernflow.modules.rag.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "workspace_document_index")
public class WorkspaceDocumentIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "source_path", nullable = false, length = 1024)
    private String sourcePath;

    @Column(name = "source_label", nullable = false, length = 1024)
    private String sourceLabel;

    @Column(name = "section_title", length = 255)
    private String sectionTitle;

    @Column(name = "source_signature", nullable = false, length = 128)
    private String sourceSignature;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected WorkspaceDocumentIndex() {
    }

    private WorkspaceDocumentIndex(
            Long userId,
            String sourcePath,
            String sourceLabel,
            String sectionTitle,
            String sourceSignature,
            int chunkCount
    ) {
        LocalDateTime now = LocalDateTime.now();
        this.userId = userId;
        this.sourcePath = sourcePath;
        this.sourceLabel = sourceLabel;
        this.sectionTitle = sectionTitle;
        this.sourceSignature = sourceSignature;
        this.chunkCount = chunkCount;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static WorkspaceDocumentIndex create(
            Long userId,
            String sourcePath,
            String sourceLabel,
            String sectionTitle,
            String sourceSignature,
            int chunkCount
    ) {
        return new WorkspaceDocumentIndex(userId, sourcePath, sourceLabel, sectionTitle, sourceSignature, chunkCount);
    }

    public void refresh(
            String sourceLabel,
            String sectionTitle,
            String sourceSignature,
            int chunkCount
    ) {
        this.sourceLabel = sourceLabel;
        this.sectionTitle = sectionTitle;
        this.sourceSignature = sourceSignature;
        this.chunkCount = chunkCount;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public String getSectionTitle() {
        return sectionTitle;
    }

    public String getSourceSignature() {
        return sourceSignature;
    }

    public int getChunkCount() {
        return chunkCount;
    }
}
