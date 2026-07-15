package com.vernflow.modules.paper.service;

import com.vernflow.common.response.PageResponse;
import com.vernflow.common.config.StorageProperties;
import com.vernflow.common.exception.BusinessException;
import com.vernflow.common.util.BigramSimilarityCalculator;
import com.vernflow.common.util.TextNormalizer;
import com.vernflow.integration.ai.AiPaperClient;
import com.vernflow.modules.paper.domain.*;
import com.vernflow.modules.paper.dto.*;
import com.vernflow.modules.paper.mapper.PaperMapper;
import com.vernflow.modules.paper.repository.*;
import com.vernflow.modules.paper.worker.PaperParsingWorker;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class PaperTaskDispatcher {

    private static final int MAX_CONCURRENT_TASKS = 2;

    private final ParseTaskRepository parseTaskRepository;
    private final PaperParsingTxService paperParsingTxService;
    private final PaperParsingWorker paperParsingWorker;
    private final AtomicBoolean dispatching = new AtomicBoolean(false);

    public PaperTaskDispatcher(
            ParseTaskRepository parseTaskRepository,
            PaperParsingTxService paperParsingTxService,
            PaperParsingWorker paperParsingWorker
    ) {
        this.parseTaskRepository = parseTaskRepository;
        this.paperParsingTxService = paperParsingTxService;
        this.paperParsingWorker = paperParsingWorker;
    }

    @Scheduled(fixedDelay = 1000L)
    public void scheduledDispatch() {
        dispatchPendingTasks();
    }

    public void dispatchPendingTasks() {
        if (!dispatching.compareAndSet(false, true)) {
            return;
        }

        try {
            while (parseTaskRepository.countByStatus(TaskStatus.PROCESSING) < MAX_CONCURRENT_TASKS) {
                PaperParsingTxService.TaskExecutionClaim claim = paperParsingTxService.claimNextPendingTask();
                if (claim == null) {
                    break;
                }
                paperParsingWorker.processClaimedTask(claim.taskId(), claim.filePath());
            }
        } finally {
            dispatching.set(false);
        }
    }
}

