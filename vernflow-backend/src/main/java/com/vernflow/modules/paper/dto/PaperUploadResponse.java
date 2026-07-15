package com.vernflow.modules.paper.dto;

import com.vernflow.modules.paper.domain.*;

import java.util.List;

public record PaperUploadResponse(int uploadedCount, List<Long> paperIds, List<Long> taskIds) {
}

