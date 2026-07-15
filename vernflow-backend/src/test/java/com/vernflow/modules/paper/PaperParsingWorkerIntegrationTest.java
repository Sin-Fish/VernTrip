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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.AopTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:paper-parsing-worker-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PaperParsingWorkerIntegrationTest {

    @Autowired
    private PaperParsingWorker paperParsingWorker;

    @Autowired
    private PaperRepository paperRepository;

    @Autowired
    private ParseTaskRepository parseTaskRepository;

    @Autowired
    private PaperChunkRepository paperChunkRepository;

    @Autowired
    private PaperReferenceRepository paperReferenceRepository;

    @MockBean
    private AiPaperClient aiPaperClient;

    @MockBean
    private PaperRelationService paperRelationService;

    private PaperParsingWorker workerTarget;

    @BeforeEach
    void setUp() throws Exception {
        workerTarget = AopTestUtils.getTargetObject(paperParsingWorker);
        reset(aiPaperClient, paperRelationService);
        cleanupData();
    }

    @AfterEach
    void tearDown() {
        cleanupData();
    }

    @Test
    void shouldPersistParseResultWhenWorkerProcessesTask() {
        Paper paper = paperRepository.saveAndFlush(Paper.create("sample-paper.pdf", "D:\\fixtures\\sample-paper.pdf"));
        ParseTask task = parseTaskRepository.saveAndFlush(ParseTask.create(paper));
        when(aiPaperClient.parsePaper("D:\\fixtures\\sample-paper.pdf")).thenReturn(buildParseResult());

        workerTarget.processTask(task.getId());

        ParseTask savedTask = parseTaskRepository.findById(task.getId()).orElseThrow();
        Paper savedPaper = paperRepository.findById(paper.getId()).orElseThrow();

        assertThat(savedTask.getStatus()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(savedTask.getErrorMessage()).isNull();
        assertThat(savedPaper.getParseStatus()).isEqualTo(ParseStatus.READY);
        assertThat(savedPaper.getTitle()).isEqualTo("RAG Foundations for Scholarly Reading");
        assertThat(savedPaper.getAuthors()).isEqualTo("Alice Chen, Bob Wang");
        assertThat(savedPaper.getVenue()).hasSizeLessThanOrEqualTo(255);
        assertThat(paperChunkRepository.findAllByPaperIdInOrderByPaperIdAscSortIndexAsc(List.of(paper.getId())))
                .hasSize(2);
        assertThat(paperReferenceRepository.findByPaperIdOrderByIdAsc(paper.getId()))
                .hasSize(2);
        verify(paperRelationService).rebuildAllRelations();
    }

    @Test
    void shouldMarkTaskFailedWhenAiServiceThrows() {
        Paper paper = paperRepository.saveAndFlush(Paper.create("failed-paper.pdf", "D:\\fixtures\\failed-paper.pdf"));
        ParseTask task = parseTaskRepository.saveAndFlush(ParseTask.create(paper));
        doThrow(new RuntimeException("AI service unavailable"))
                .when(aiPaperClient)
                .parsePaper("D:\\fixtures\\failed-paper.pdf");

        workerTarget.processTask(task.getId());

        ParseTask savedTask = parseTaskRepository.findById(task.getId()).orElseThrow();
        Paper savedPaper = paperRepository.findById(paper.getId()).orElseThrow();

        assertThat(savedTask.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(savedTask.getErrorMessage()).contains("AI service unavailable");
        assertThat(savedPaper.getParseStatus()).isEqualTo(ParseStatus.FAILED);
        verify(paperRelationService, never()).rebuildAllRelations();
    }

    private AiPaperClient.ParsePdfResponse buildParseResult() {
        return new AiPaperClient.ParsePdfResponse(
                "RAG Foundations for Scholarly Reading",
                List.of("Alice Chen", "Bob Wang"),
                2024,
                "Foundations of Retrieval-Augmented Reading for Academic Papers "
                        + "Lina Zhang, Qiao Liu, Ming Zhao, and the Scholarly Systems Workshop "
                        + "Proceedings of the International Conference on Long-form Scientific Reading Interfaces 2024",
                "This paper studies retrieval-augmented reading assistants.",
                "The paper proposes a retrieval-augmented academic reading workflow.",
                "Researchers need grounded support when reading long academic PDFs.",
                "The system combines PDF parsing, chunk retrieval, and evidence-based answers.",
                "The workflow improves answer grounding and reading efficiency.",
                "It is a practical MVP path for AI-assisted literature review.",
                List.of("RAG", "citation graph", "academic reading"),
                List.of(
                        new AiPaperClient.ReferenceItem(
                                "[1] Alice Chen. Knowledge Graph Retrieval for Science. 2022.",
                                "Knowledge Graph Retrieval for Science",
                                "Alice Chen",
                                2022
                        ),
                        new AiPaperClient.ReferenceItem(
                                "[2] Bob Wang. Evidence-Centric QA for Research Papers. 2023.",
                                "Evidence-Centric QA for Research Papers",
                                "Bob Wang",
                                2023
                        )
                ),
                List.of(
                        new AiPaperClient.ChunkItem(
                                "Abstract",
                                1,
                                "This paper studies retrieval-augmented reading assistants.",
                                "chunk-1"
                        ),
                        new AiPaperClient.ChunkItem(
                                "Method",
                                2,
                                "The system combines parsing, chunking, and evidence ranking.",
                                "chunk-2"
                        )
                )
        );
    }

    private void cleanupData() {
        parseTaskRepository.deleteAllInBatch();
        paperReferenceRepository.deleteAllInBatch();
        paperChunkRepository.deleteAllInBatch();
        paperRepository.deleteAllInBatch();
    }
}

