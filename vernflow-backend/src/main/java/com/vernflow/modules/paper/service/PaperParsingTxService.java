package com.vernflow.modules.paper.service;

import com.vernflow.common.exception.BusinessException;
import com.vernflow.integration.ai.AiPaperClient;
import com.vernflow.modules.paper.domain.Paper;
import com.vernflow.modules.paper.domain.PaperChunk;
import com.vernflow.modules.paper.domain.PaperReference;
import com.vernflow.modules.paper.domain.ParseTask;
import com.vernflow.modules.paper.domain.TaskStatus;
import com.vernflow.modules.paper.repository.ParseTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class PaperParsingTxService {

    private final ParseTaskRepository parseTaskRepository;

    public PaperParsingTxService(ParseTaskRepository parseTaskRepository) {
        this.parseTaskRepository = parseTaskRepository;
    }

    @Transactional
    public TaskExecutionClaim claimNextPendingTask() {
        ParseTask parseTask = parseTaskRepository.findTopByStatusOrderByQueueOrderAscCreatedAtAsc(TaskStatus.PENDING)
                .orElse(null);
        if (parseTask == null) {
            return null;
        }
        parseTask.markProcessing();
        parseTask.getPaper().markProcessing();
        return new TaskExecutionClaim(parseTask.getId(), parseTask.getPaper().getFilePath());
    }

    @Transactional
    public String markTaskProcessing(Long taskId) {
        ParseTask parseTask = loadTask(taskId);
        parseTask.markProcessing();
        parseTask.getPaper().markProcessing();
        return parseTask.getPaper().getFilePath();
    }

    @Transactional
    public void saveParseResult(Long taskId, AiPaperClient.ParsePdfResponse parseResult) {
        ParseTask parseTask = loadTask(taskId);
        Paper paper = parseTask.getPaper();
        paper.applyParseResult(parseResult);
        paper.replaceChunks(buildChunks(parseResult.chunks()));
        paper.replaceReferences(buildReferences(parseResult.references()));
        parseTask.markSuccess();
    }

    @Transactional
    public void markTaskFailed(Long taskId, String message) {
        ParseTask parseTask = loadTask(taskId);
        parseTask.markFailed(message);
        parseTask.getPaper().markFailed();
    }

    private List<PaperChunk> buildChunks(List<AiPaperClient.ChunkItem> chunkItems) {
        List<PaperChunk> chunks = new ArrayList<>();
        for (int index = 0; index < chunkItems.size(); index++) {
            AiPaperClient.ChunkItem chunkItem = chunkItems.get(index);
            chunks.add(PaperChunk.create(
                    chunkItem.sectionTitle(),
                    chunkItem.pageNo(),
                    chunkItem.chunkText(),
                    chunkItem.embeddingId(),
                    index
            ));
        }
        return chunks;
    }

    private List<PaperReference> buildReferences(List<AiPaperClient.ReferenceItem> referenceItems) {
        List<PaperReference> references = new ArrayList<>();
        for (AiPaperClient.ReferenceItem referenceItem : referenceItems) {
            references.add(PaperReference.create(
                    referenceItem.rawReference(),
                    referenceItem.refTitle(),
                    referenceItem.refAuthors(),
                    referenceItem.refYear()
            ));
        }
        return references;
    }

    private ParseTask loadTask(Long taskId) {
        return parseTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("未找到对应解析任务: " + taskId));
    }

    public record TaskExecutionClaim(Long taskId, String filePath) {
    }
}
