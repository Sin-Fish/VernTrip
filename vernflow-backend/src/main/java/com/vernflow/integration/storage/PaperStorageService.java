package com.vernflow.integration.storage;

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

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 璁烘枃鍘熸枃浠跺瓨鍌ㄦ湇鍔°€? */
@Service
public class PaperStorageService {

    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final StorageProperties storageProperties;

    public PaperStorageService(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    /**
     * 淇濆瓨涓婁紶鐨?PDF 鏂囦欢銆?     *
     * @param file 涓婁紶鏂囦欢
     * @return 宸蹭繚瀛樻枃浠剁殑鍏冧俊鎭?     */
    public StoredPaperFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }

        String originalFileName = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFileName)) {
            throw new BusinessException("上传文件必须包含文件名");
        }
        if (!originalFileName.toLowerCase().endsWith(".pdf")) {
            throw new BusinessException("当前 MVP 仅支持 PDF 文件上传");
        }

        Path uploadDir = storageProperties.paperUploadDir().normalize().toAbsolutePath();
        String sanitizedName = originalFileName.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\._-]", "_");
        String storedFileName = FILE_TIME_FORMATTER.format(LocalDateTime.now()) + "_"
                + UUID.randomUUID().toString().substring(0, 8) + "_" + sanitizedName;
        Path targetPath = uploadDir.resolve(storedFileName);

        try {
            Files.createDirectories(uploadDir);
            file.transferTo(targetPath);
            return new StoredPaperFile(originalFileName, targetPath);
        } catch (IOException exception) {
            throw new BusinessException("保存论文文件失败: " + exception.getMessage());
        }
    }

    /**
     * 鍒犻櫎宸蹭繚瀛樼殑鍘熷鏂囦欢銆?     *
     * @param filePath 鏂囦欢璺緞
     */
    public void deleteStoredFile(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return;
        }

        Path uploadDir = storageProperties.paperUploadDir().normalize().toAbsolutePath();
        Path targetPath = Path.of(filePath).normalize().toAbsolutePath();
        if (!targetPath.startsWith(uploadDir)) {
            throw new BusinessException("拒绝删除存储目录之外的文件");
        }

        try {
            Files.deleteIfExists(targetPath);
        } catch (IOException exception) {
            throw new BusinessException("删除原文文件失败: " + exception.getMessage());
        }
    }

    /**
     * 宸蹭繚瀛樻枃浠剁殑鍏冧俊鎭€?     *
     * @param originalFileName 鍘熷鏂囦欢鍚?     * @param filePath 淇濆瓨璺緞
     */
    public record StoredPaperFile(String originalFileName, Path filePath) {
    }
}

