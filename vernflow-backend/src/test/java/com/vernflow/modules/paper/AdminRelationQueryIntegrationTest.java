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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-relation-query-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AdminRelationQueryIntegrationTest {

    @Autowired
    private PaperRepository paperRepository;

    @Autowired
    private PaperRelationRepository paperRelationRepository;

    @Autowired
    private PaperQueryService paperQueryService;

    @AfterEach
    void tearDown() {
        paperRelationRepository.deleteAllInBatch();
        paperRepository.deleteAllInBatch();
    }

    @Test
    void shouldListAdminRelations() {
        Paper source = paperRepository.saveAndFlush(Paper.create(
                "source-paper.pdf",
                Path.of("./build/test-storage/source-paper.pdf").toAbsolutePath().normalize().toString()
        ));
        source.updateMetadata(new UpdatePaperRequest("Source Paper", "Alice", 2024, null, null, null, null, null, null, null, null));
        paperRepository.saveAndFlush(source);

        Paper target = paperRepository.saveAndFlush(Paper.create(
                "target-paper.pdf",
                Path.of("./build/test-storage/target-paper.pdf").toAbsolutePath().normalize().toString()
        ));
        target.updateMetadata(new UpdatePaperRequest("Target Paper", "Bob", 2023, null, null, null, null, null, null, null, null));
        paperRepository.saveAndFlush(target);

        paperRelationRepository.saveAndFlush(PaperRelation.create(
                source,
                target,
                RelationType.CITES,
                0.95,
                "Reference evidence"
        ));

        PageResponse<AdminRelationResponse> response = paperQueryService.listRelations(source.getId(), 0, 10);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).sourcePaperTitle()).isEqualTo("Source Paper");
        assertThat(response.items().get(0).targetPaperTitle()).isEqualTo("Target Paper");
        assertThat(response.items().get(0).confidence()).isEqualTo(0.95);
    }
}

