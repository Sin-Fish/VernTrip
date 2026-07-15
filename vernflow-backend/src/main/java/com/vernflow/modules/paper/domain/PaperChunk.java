package com.vernflow.modules.paper.domain;

import com.vernflow.integration.ai.AiPaperClient;
import com.vernflow.modules.paper.dto.UpdatePaperRequest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "paper_chunk")
public class PaperChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paper_id", nullable = false)
    private Paper paper;

    @Column(name = "section_title", length = 255)
    private String sectionTitle;

    @Column(name = "page_no")
    private Integer pageNo;

    @Column(name = "chunk_text", nullable = false, columnDefinition = "TEXT")
    private String chunkText;

    @Column(name = "embedding_id", length = 120)
    private String embeddingId;

    @Column(name = "sort_index", nullable = false)
    private Integer sortIndex;

    protected PaperChunk() {
    }

    private PaperChunk(String sectionTitle, Integer pageNo, String chunkText, String embeddingId, Integer sortIndex) {
        this.sectionTitle = sectionTitle;
        this.pageNo = pageNo;
        this.chunkText = chunkText;
        this.embeddingId = embeddingId;
        this.sortIndex = sortIndex;
    }

    public static PaperChunk create(String sectionTitle, Integer pageNo, String chunkText, String embeddingId, Integer sortIndex) {
        return new PaperChunk(sectionTitle, pageNo, chunkText, embeddingId, sortIndex);
    }

    public void attachTo(Paper paper) {
        this.paper = paper;
    }

    public Long getId() {
        return id;
    }

    public Paper getPaper() {
        return paper;
    }

    public String getSectionTitle() {
        return sectionTitle;
    }

    public Integer getPageNo() {
        return pageNo;
    }

    public String getChunkText() {
        return chunkText;
    }

    public String getEmbeddingId() {
        return embeddingId;
    }

    public Integer getSortIndex() {
        return sortIndex;
    }
}

