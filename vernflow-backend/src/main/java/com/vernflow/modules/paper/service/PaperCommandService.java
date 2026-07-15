package com.vernflow.modules.paper.service;

import com.vernflow.common.response.PageResponse;
import com.vernflow.common.config.StorageProperties;
import com.vernflow.common.exception.BusinessException;
import com.vernflow.common.util.BigramSimilarityCalculator;
import com.vernflow.common.util.TextNormalizer;
import com.vernflow.integration.ai.AiPaperClient;
import com.vernflow.integration.storage.PaperStorageService;
import com.vernflow.modules.paper.domain.*;
import com.vernflow.modules.paper.dto.*;
import com.vernflow.modules.paper.mapper.PaperMapper;
import com.vernflow.modules.paper.repository.*;
import com.vernflow.modules.paper.worker.PaperParsingWorker;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
public class PaperCommandService {

    private static final List<TaskStatus> QUEUE_ORDER_STATUSES = List.of(TaskStatus.PENDING, TaskStatus.PAUSED);

    private final PaperRepository paperRepository;
    private final ParseTaskRepository parseTaskRepository;
    private final PaperStorageService paperStorageService;
    private final PaperTaskDispatcher paperTaskDispatcher;
    private final PaperRelationService paperRelationService;
    private final PaperQueryService paperQueryService;

    public PaperCommandService(
            PaperRepository paperRepository,
            ParseTaskRepository parseTaskRepository,
            PaperStorageService paperStorageService,
            PaperTaskDispatcher paperTaskDispatcher,
            PaperRelationService paperRelationService,
            PaperQueryService paperQueryService
    ) {
        this.paperRepository = paperRepository;
        this.parseTaskRepository = parseTaskRepository;
        this.paperStorageService = paperStorageService;
        this.paperTaskDispatcher = paperTaskDispatcher;
        this.paperRelationService = paperRelationService;
        this.paperQueryService = paperQueryService;
    }

    public PaperUploadResponse uploadPapers(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException("请至少选择一个 PDF 文件");
        }

        List<Long> paperIds = new ArrayList<>();
        List<Long> taskIds = new ArrayList<>();
        long nextQueueOrder = nextQueueOrder();

        for (MultipartFile file : files) {
            PaperStorageService.StoredPaperFile storedFile = paperStorageService.store(file);
            Paper paper = paperRepository.saveAndFlush(
                    Paper.create(storedFile.originalFileName(), storedFile.filePath().toString())
            );
            ParseTask parseTask = parseTaskRepository.saveAndFlush(ParseTask.create(paper, nextQueueOrder++));
            paperIds.add(paper.getId());
            taskIds.add(parseTask.getId());
        }

        dispatchQueueAfterCommit();
        return new PaperUploadResponse(files.size(), paperIds, taskIds);
    }

    @Transactional
    public PaperDetailResponse updatePaper(Long paperId, UpdatePaperRequest request) {
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new BusinessException("未找到对应论文"));
        paper.updateMetadata(request);
        paperRelationService.rebuildAllRelations();
        return paperQueryService.getPaperDetail(paperId);
    }

    @Transactional
    public void retryTask(Long taskId) {
        ParseTask parseTask = loadTask(taskId);
        parseTask.resetForRetry(nextQueueOrder());
        parseTask.getPaper().markQueued();
        dispatchQueueAfterCommit();
    }

    @Transactional
    public void moveTaskToFront(Long taskId) {
        ParseTask parseTask = loadTask(taskId);
        parseTask.moveToFront(nextFrontQueueOrder());
        dispatchQueueAfterCommit();
    }

    @Transactional
    public void pauseTask(Long taskId) {
        ParseTask parseTask = loadTask(taskId);
        parseTask.pause();
    }

    @Transactional
    public void resumeTask(Long taskId) {
        ParseTask parseTask = loadTask(taskId);
        parseTask.resume();
        parseTask.getPaper().markQueued();
        dispatchQueueAfterCommit();
    }

    @Transactional
    public void deleteTask(Long taskId) {
        ParseTask parseTask = loadTask(taskId);
        if (parseTask.getStatus() == TaskStatus.PROCESSING) {
            throw new BusinessException("处理中任务不能删除");
        }

        Paper paper = parseTask.getPaper();
        Long paperId = paper.getId();
        String filePath = paper.getFilePath();
        ParseStatus parseStatus = paper.getParseStatus();

        parseTaskRepository.delete(parseTask);
        parseTaskRepository.flush();

        if (parseTaskRepository.countByPaperId(paperId) == 0 && parseStatus != ParseStatus.READY) {
            paperRepository.delete(paper);
            paperRepository.flush();
            paperStorageService.deleteStoredFile(filePath);
        }

        dispatchQueueAfterCommit();
    }

    private ParseTask loadTask(Long taskId) {
        return parseTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("未找到对应解析任务"));
    }

    private long nextQueueOrder() {
        return Math.max(System.currentTimeMillis(), parseTaskRepository.findMaxQueueOrderByStatuses(QUEUE_ORDER_STATUSES) + 1L);
    }

    private long nextFrontQueueOrder() {
        return parseTaskRepository.findMinQueueOrderByStatuses(QUEUE_ORDER_STATUSES) - 1L;
    }

    private void dispatchQueueAfterCommit() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            paperTaskDispatcher.dispatchPendingTasks();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                paperTaskDispatcher.dispatchPendingTasks();
            }
        });
    }
}

