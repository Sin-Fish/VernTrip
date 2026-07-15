package com.vernflow.modules.paper;

import com.vernflow.modules.paper.controller.*;
import com.vernflow.modules.paper.domain.*;
import com.vernflow.modules.paper.dto.*;
import com.vernflow.modules.paper.mapper.*;
import com.vernflow.modules.paper.repository.*;
import com.vernflow.modules.paper.service.*;
import com.vernflow.modules.paper.worker.*;


import com.vernflow.common.response.PageResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:paper-query-service-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PaperQueryServiceIntegrationTest {

    @Autowired
    private PaperRepository paperRepository;

    @Autowired
    private PaperQueryService paperQueryService;

    @AfterEach
    void tearDown() {
        paperRepository.deleteAllInBatch();
    }

    @Test
    void shouldSearchPapersWithoutTriggeringClobLowerError() {
        String filePath = Path.of("./build/test-storage/query-service-paper.pdf").toAbsolutePath().normalize().toString();
        Paper paper = Paper.create("rag-reader-paper.pdf", filePath);
        paper.updateMetadata(new UpdatePaperRequest(
                "RAG Reader Foundations",
                "Alice Chen",
                2024,
                "Reading Systems Workshop",
                "Long abstract content that would be mapped as a large object in the database.",
                "This summary talks about a retrieval-augmented reading workflow.",
                "What information should a reading assistant surface?",
                "The system combines parsing, retrieval, and evidence highlighting.",
                "The workflow improves grounded reading support.",
                "Useful for literature review MVPs.",
                List.of("RAG", "reading assistant")
        ));
        paperRepository.saveAndFlush(paper);

        PageResponse<PaperItemResponse> response = paperQueryService.listPapers("rag", null, 0, 10);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).title()).isEqualTo("RAG Reader Foundations");
    }
}

