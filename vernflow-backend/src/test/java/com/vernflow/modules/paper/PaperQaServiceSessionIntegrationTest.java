package com.vernflow.modules.paper;

import com.vernflow.integration.ai.AiPaperClient;
import com.vernflow.modules.paper.controller.*;
import com.vernflow.modules.paper.domain.*;
import com.vernflow.modules.paper.dto.*;
import com.vernflow.modules.paper.mapper.*;
import com.vernflow.modules.paper.repository.*;
import com.vernflow.modules.paper.service.*;
import com.vernflow.modules.paper.worker.*;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:paper-qa-session-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PaperQaServiceSessionIntegrationTest {

    @Autowired
    private PaperQaService paperQaService;

    @Autowired
    private PaperRepository paperRepository;

    @Autowired
    private QaSessionRepository qaSessionRepository;

    @MockBean
    private AiPaperClient aiPaperClient;

    @AfterEach
    void tearDown() {
        qaSessionRepository.deleteAll();
        paperRepository.deleteAll();
    }

    @Test
    void shouldPersistQaSessionAndEvidenceWhenAnsweringQuestion() {
        String filePath = Path.of("./build/test-storage/qa-session-paper.pdf").toAbsolutePath().normalize().toString();
        Paper paper = Paper.create("qa-session-paper.pdf", filePath);
        paper.updateMetadata(new UpdatePaperRequest(
                "Evidence Grounded Reading",
                "Alice Chen",
                2025,
                "Reading Systems",
                "A paper about evidence-grounded reading assistants.",
                "Summary",
                "Problem",
                "Method",
                "Conclusion",
                "Value",
                List.of("reading", "rag")
        ));
        paper.addChunk(PaperChunk.create("Introduction", 1, "Evidence grounded reading uses retrieved passages.", "chunk-1", 0));
        Paper savedPaper = paperRepository.saveAndFlush(paper);

        when(aiPaperClient.askQuestion(eq("What does the paper study?"), eq(QaScope.CURRENT_PAPER), any()))
                .thenReturn(new AiPaperClient.RagQueryResponse(
                        "It studies evidence-grounded reading assistants.",
                        List.of(new AiPaperClient.RagEvidence(
                                savedPaper.getId(),
                                savedPaper.getTitle(),
                                "Introduction",
                                1,
                                "Evidence grounded reading uses retrieved passages.",
                                0.98
                        ))
                ));

        QaResponse response = paperQaService.ask(new QaRequest(
                null,
                savedPaper.getId(),
                QaScope.CURRENT_PAPER,
                "What does the paper study?"
        ));

        assertThat(response.answer()).isEqualTo("It studies evidence-grounded reading assistants.");
        List<QaSessionSummaryResponse> sessions = paperQaService.listSessions(savedPaper.getId());
        assertThat(sessions).hasSize(1);

        QaSessionDetailResponse session = paperQaService.getSession(sessions.get(0).id());
        assertThat(session.paperId()).isEqualTo(savedPaper.getId());
        assertThat(session.question()).isEqualTo("What does the paper study?");
        assertThat(session.answer()).isEqualTo("It studies evidence-grounded reading assistants.");
        assertThat(session.evidences()).hasSize(1);
        assertThat(session.evidences().get(0).paperId()).isEqualTo(savedPaper.getId());
        assertThat(session.evidences().get(0).sectionTitle()).isEqualTo("Introduction");
    }
}

