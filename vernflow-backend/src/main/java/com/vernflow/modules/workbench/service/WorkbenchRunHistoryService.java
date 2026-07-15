package com.vernflow.modules.workbench.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.common.exception.BusinessException;
import com.vernflow.modules.workbench.dto.WorkbenchRunDetailResponse;
import com.vernflow.modules.workbench.model.WorkbenchRunHistoryPage;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class WorkbenchRunHistoryService {
    private static final TypeReference<List<WorkbenchRunHistoryPage>> WORKBENCH_HISTORY_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public WorkbenchRunHistoryService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String appendHistoryPage(String historyJson, WorkbenchRunDetailResponse detail) {
        List<WorkbenchRunHistoryPage> historyPages = new ArrayList<>(deserializeHistoryPages(historyJson));
        historyPages.add(toHistoryPage(detail));
        return serializeHistoryPages(historyPages);
    }

    public String serializeHistoryPages(List<WorkbenchRunHistoryPage> historyPages) {
        try {
            return objectMapper.writeValueAsString(historyPages == null ? List.of() : historyPages);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("Failed to persist workbench page history.");
        }
    }

    public List<WorkbenchRunHistoryPage> deserializeHistoryPages(String historyJson) {
        if (!StringUtils.hasText(historyJson)) {
            return List.of();
        }

        try {
            return objectMapper.readValue(historyJson, WORKBENCH_HISTORY_TYPE);
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private WorkbenchRunHistoryPage toHistoryPage(WorkbenchRunDetailResponse detail) {
        return new WorkbenchRunHistoryPage(
                detail.name(),
                detail.skills(),
                detail.status(),
                detail.statusTone(),
                detail.inspectMode(),
                detail.planSummary(),
                detail.prompt(),
                detail.globalScope(),
                detail.workingFiles(),
                detail.selectedNodeId(),
                detail.activeExecutionNodeId(),
                detail.activeInspectionNodeId(),
                detail.nodes(),
                detail.graphLinks(),
                detail.graphMainPath(),
                detail.time()
        );
    }
}
