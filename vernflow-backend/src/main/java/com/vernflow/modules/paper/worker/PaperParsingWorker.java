package com.vernflow.modules.paper.worker;

import com.vernflow.integration.ai.AiPaperClient;
import com.vernflow.modules.paper.domain.*;
import com.vernflow.modules.paper.service.*;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class PaperParsingWorker {

    private final AiPaperClient aiPaperClient;
    private final PaperRelationService paperRelationService;
    private final PaperParsingTxService paperParsingTxService;

    public PaperParsingWorker(
            AiPaperClient aiPaperClient,
            PaperRelationService paperRelationService,
            PaperParsingTxService paperParsingTxService
    ) {
        this.aiPaperClient = aiPaperClient;
        this.paperRelationService = paperRelationService;
        this.paperParsingTxService = paperParsingTxService;
    }

    public void processTask(Long taskId) {
        String filePath = paperParsingTxService.markTaskProcessing(taskId);
        runParsing(taskId, filePath);
    }

    @Async("paperTaskExecutor")
    public void processClaimedTask(Long taskId, String filePath) {
        runParsing(taskId, filePath);
    }

    private void runParsing(Long taskId, String filePath) {
        try {
            AiPaperClient.ParsePdfResponse parseResult = aiPaperClient.parsePaper(filePath);
            paperParsingTxService.saveParseResult(taskId, parseResult);
            paperRelationService.rebuildAllRelations();
        } catch (Exception exception) {
            paperParsingTxService.markTaskFailed(taskId, "解析失败: " + exception.getMessage());
        }
    }
}

