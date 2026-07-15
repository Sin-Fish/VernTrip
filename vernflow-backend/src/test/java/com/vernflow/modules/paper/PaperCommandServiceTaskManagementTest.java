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
import org.springframework.boot.test.mock.mockito.MockBean;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:paper-command-task-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.storage.paper-upload-dir=./build/test-storage"
})
class PaperCommandServiceTaskManagementTest {

    @Autowired
    private PaperCommandService paperCommandService;

    @Autowired
    private PaperRepository paperRepository;

    @Autowired
    private ParseTaskRepository parseTaskRepository;

    @MockBean
    private PaperTaskDispatcher paperTaskDispatcher;

    @AfterEach
    void tearDown() {
        parseTaskRepository.deleteAllInBatch();
        paperRepository.deleteAllInBatch();
    }

    @Test
    void shouldMovePauseAndResumeQueuedTask() {
        Paper firstPaper = paperRepository.saveAndFlush(Paper.create(
                "first-paper.pdf",
                Path.of("./build/test-storage/first-paper.pdf").toAbsolutePath().normalize().toString()
        ));
        Paper secondPaper = paperRepository.saveAndFlush(Paper.create(
                "second-paper.pdf",
                Path.of("./build/test-storage/second-paper.pdf").toAbsolutePath().normalize().toString()
        ));

        ParseTask firstTask = parseTaskRepository.saveAndFlush(ParseTask.create(firstPaper, 100));
        ParseTask secondTask = parseTaskRepository.saveAndFlush(ParseTask.create(secondPaper, 200));

        paperCommandService.moveTaskToFront(secondTask.getId());
        paperCommandService.pauseTask(firstTask.getId());
        paperCommandService.resumeTask(firstTask.getId());

        ParseTask savedFirstTask = parseTaskRepository.findById(firstTask.getId()).orElseThrow();
        ParseTask savedSecondTask = parseTaskRepository.findById(secondTask.getId()).orElseThrow();

        assertThat(savedSecondTask.getQueueOrder()).isLessThan(savedFirstTask.getQueueOrder());
        assertThat(savedFirstTask.getStatus()).isEqualTo(TaskStatus.PENDING);
    }

    @Test
    void shouldDeletePlaceholderPaperWhenDeletingItsOnlyNonReadyTask() {
        Paper paper = paperRepository.saveAndFlush(Paper.create(
                "delete-me.pdf",
                Path.of("./build/test-storage/delete-me.pdf").toAbsolutePath().normalize().toString()
        ));
        ParseTask task = parseTaskRepository.saveAndFlush(ParseTask.create(paper, 300));

        paperCommandService.deleteTask(task.getId());

        assertThat(parseTaskRepository.findById(task.getId())).isEmpty();
        assertThat(paperRepository.findById(paper.getId())).isEmpty();
    }
}

