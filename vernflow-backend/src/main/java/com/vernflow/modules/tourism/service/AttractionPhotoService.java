package com.vernflow.modules.tourism.service;

import com.vernflow.common.config.StorageProperties;
import com.vernflow.integration.amap.AmapPoiClient;
import com.vernflow.modules.tourism.domain.TourismResource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 景点实景照片解析：优先用高德 POI 照片（按 amapPoiId 拉取并缓存到本地），
 * 无照片时返回空列表，由调用方走兜底素材。保证 AI 成片画面为景点真实影像。
 */
@Service
public class AttractionPhotoService {

    private static final Logger log = LoggerFactory.getLogger(AttractionPhotoService.class);
    private static final int MAX_PHOTOS = 3;

    private final AmapPoiClient amapPoiClient;
    private final StorageProperties storageProperties;

    public AttractionPhotoService(AmapPoiClient amapPoiClient, StorageProperties storageProperties) {
        this.amapPoiClient = amapPoiClient;
        this.storageProperties = storageProperties;
    }

    /** 返回景点的实景照片 base64 列表（JPEG），最多 3 张；无可用照片时返回空列表。 */
    public List<String> resolvePhotoBase64(TourismResource resource) {
        if (resource == null) {
            return List.of();
        }
        List<Path> files = cachedPhotos(resource);
        if (files.isEmpty()) {
            files = fetchAndCache(resource);
        }
        List<String> result = new ArrayList<>();
        for (Path file : files) {
            try {
                result.add(Base64.getEncoder().encodeToString(Files.readAllBytes(file)));
            } catch (IOException exception) {
                log.warn("Failed to read cached photo {}.", file, exception);
            }
        }
        return result;
    }

    private List<Path> cachedPhotos(TourismResource resource) {
        List<Path> files = new ArrayList<>();
        try (var entries = Files.list(photoDir(resource))) {
            entries.filter(path -> path.getFileName().toString().endsWith(".jpg"))
                    .sorted()
                    .limit(MAX_PHOTOS)
                    .forEach(files::add);
        } catch (IOException ignored) {
            // 缓存目录不存在等，视为无缓存。
        }
        return files;
    }

    private List<Path> fetchAndCache(TourismResource resource) {
        try {
            List<String> urls = amapPoiClient.fetchPhotos(
                    resource.getAmapPoiId(), resource.getName(), resource.getCity()
            );
            List<Path> saved = new ArrayList<>();
            for (int index = 0; index < urls.size() && saved.size() < MAX_PHOTOS; index++) {
                byte[] photo = amapPoiClient.downloadPhoto(urls.get(index));
                if (photo == null || photo.length == 0) {
                    continue;
                }
                Path target = photoDir(resource).resolve("photo-" + (index + 1) + ".jpg");
                Files.createDirectories(target.getParent());
                Files.write(target, photo);
                saved.add(target);
            }
            return saved;
        } catch (Exception exception) {
            log.warn("Failed to fetch photos for resource {}.", resource.getId(), exception);
            return List.of();
        }
    }

    private Path photoDir(TourismResource resource) {
        return storageProperties.mediaDir().resolve("photos").resolve(String.valueOf(resource.getId()));
    }
}
