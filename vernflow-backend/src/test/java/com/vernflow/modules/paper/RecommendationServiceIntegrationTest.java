package com.vernflow.modules.paper;

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

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:recommendation-service-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class RecommendationServiceIntegrationTest {

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private PaperRepository paperRepository;

    @Autowired
    private ExternalPaperRepository externalPaperRepository;

    @AfterEach
    void tearDown() {
        externalPaperRepository.deleteAllInBatch();
        paperRepository.deleteAll();
    }

    @Test
    void shouldSeedRecommendationsFromUnmatchedReferencesAndUpdateStatus() {
        String filePath = Path.of("./build/test-storage/recommendation-paper.pdf").toAbsolutePath().normalize().toString();
        Paper paper = Paper.create("recommendation-paper.pdf", filePath);
        paper.addReference(PaperReference.create(
                "[1] Alice Chen. Knowledge Graph Retrieval for Science. 2024.",
                "Knowledge Graph Retrieval for Science",
                "Alice Chen",
                2024
        ));
        paperRepository.saveAndFlush(paper);

        List<ExternalPaperResponse> recommendations = recommendationService.listRandomRecommendations(6);

        assertThat(recommendations).hasSize(1);
        assertThat(recommendations.get(0).title()).isEqualTo("Knowledge Graph Retrieval for Science");
        assertThat(recommendations.get(0).status()).isEqualTo(ExternalPaperStatus.PENDING);

        ExternalPaperResponse imported = recommendationService.importRecommendation(recommendations.get(0).id());
        assertThat(imported.status()).isEqualTo(ExternalPaperStatus.IMPORTED);

        ExternalPaperResponse ignored = recommendationService.ignoreRecommendation(imported.id());
        assertThat(ignored.status()).isEqualTo(ExternalPaperStatus.IGNORED);
    }
}

