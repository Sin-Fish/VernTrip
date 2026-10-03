package com.vernflow.modules.tourism.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.modules.tourism.domain.TourismResource;
import com.vernflow.modules.tourism.dto.MerchantProductResponse;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 中文视觉化视频提示词构建器（可灵文生视频用）。
 * 基于已核验的资源事实按 category 差异化拼装镜头与风格描述，不依赖 LLM。
 * 可灵 prompt 上限 2500 字符，本类以 2500 兜底截断。
 */
@Component
public class VideoPromptBuilder {

    private static final int MAX_PROMPT_LENGTH = 2500;
    private static final int MAX_SUMMARY_LENGTH = 120;

    private static final String DEFAULT_HINT = "突出当地特色与沉浸式旅行氛围，结尾定格于最具代表性的画面";

    private static final String CAMERA_INSTRUCTIONS =
            "航拍全景开场，缓慢推进至细节特写，自然光影流转，运镜平稳，电影级调色";

    private static final String STYLE_INSTRUCTIONS =
            "写实摄影风格，画面干净通透；无文字、无水印、无字幕、无 logo；人物自然不直视镜头";

    private final ObjectMapper objectMapper;

    public VideoPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String buildResourcePrompt(TourismResource resource, String promptHint) {
        String subject = resource.getName() + "（" + resource.getCity() + "·" + resource.getDistrict() + "）";
        String scene = categoryScene(resource.getCategory()) + "，" + abbreviate(resource.getSummary(), MAX_SUMMARY_LENGTH);
        String facts = "开放时间" + fallback(resource.getOpenHours(), "以现场为准")
                + "；参考花费" + priceRange(resource.getPriceMin(), resource.getPriceMax())
                + "；特色标签：" + topTags(resource.getTagsJson());
        return compose(subject, scene, facts, promptHint);
    }

    public String buildProductPrompt(MerchantProductResponse product, String promptHint) {
        String subject = product.name() + "（" + product.targetAudience() + "，" + product.durationDays() + " 天行程）";
        String highlights = product.highlights() == null || product.highlights().isEmpty()
                ? abbreviate(product.description(), MAX_SUMMARY_LENGTH)
                : abbreviate(String.join("，", product.highlights()), MAX_SUMMARY_LENGTH);
        String resourceNames = product.resources() == null ? "" : product.resources().stream()
                .map(MerchantProductResponse.ResourceSummary::name)
                .limit(3)
                .collect(Collectors.joining("、"));
        String facts = "参考价格¥" + product.price().stripTrailingZeros().toPlainString()
                + (resourceNames.isEmpty() ? "" : "；包含：") + resourceNames;
        return compose(subject, highlights, facts, promptHint);
    }

    /** 资源事实文本（AI 成片解说词的依据）。 */
    public String buildFactsText(TourismResource resource) {
        return "开放时间" + fallback(resource.getOpenHours(), "以现场为准")
                + "；参考花费" + priceRange(resource.getPriceMin(), resource.getPriceMax())
                + "；特色标签：" + topTags(resource.getTagsJson())
                + "；简介：" + abbreviate(resource.getSummary(), 60);
    }

    /** 产品事实文本（AI 成片解说词的依据）。 */
    public String buildProductFactsText(MerchantProductResponse product) {
        String highlights = product.highlights() == null || product.highlights().isEmpty()
                ? ""
                : "；亮点：" + abbreviate(String.join("、", product.highlights()), 80);
        return "参考价格¥" + product.price().stripTrailingZeros().toPlainString()
                + "；目标客群：" + product.targetAudience()
                + "；行程时长：" + product.durationDays() + " 天"
                + highlights;
    }

    private String compose(String subject, String scene, String facts, String promptHint) {
        String hint = StringUtils.hasText(promptHint) ? promptHint.trim() : DEFAULT_HINT;
        String prompt = """
                【画面主体】%s
                【场景描写】%s
                【事实细节】%s
                【镜头语言】%s
                【风格要求】%s
                【补充要求】%s
                """.formatted(subject, scene, facts, CAMERA_INSTRUCTIONS, STYLE_INSTRUCTIONS, hint);
        return prompt.length() <= MAX_PROMPT_LENGTH ? prompt : prompt.substring(0, MAX_PROMPT_LENGTH);
    }

    private String categoryScene(TourismResource.Category category) {
        return switch (category) {
            case SCENIC -> "自然风光：晨雾山峦与水面倒影，四季植被色彩，远景辽阔层次分明";
            case CULTURE -> "历史人文：古建筑飞檐斗拱细节，室内展陈暖光，青石板街巷纵深";
            case EXPERIENCE -> "体验活动：手作过程特写，参与者专注神情与成品展示，节奏明快";
            case FOOD -> "美食餐饮：食材与成品特写，热气蒸腾，摆盘与餐具质感，暖色调";
            case LODGING -> "住宿空间：房间全景推入，窗外景色，床品与灯光细节，温馨静谧";
            case LEISURE -> "城市休闲：街景人流自然往来，黄昏光效，轻松慢节奏氛围";
        };
    }

    private String topTags(String tagsJson) {
        if (!StringUtils.hasText(tagsJson)) {
            return "当地特色体验";
        }
        try {
            List<String> tags = objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {
            });
            if (tags.isEmpty()) {
                return "当地特色体验";
            }
            return tags.stream().limit(3).collect(Collectors.joining("、"));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read resource tags.", exception);
        }
    }

    private String priceRange(BigDecimal priceMin, BigDecimal priceMax) {
        if (priceMin == null && priceMax == null) {
            return "以现场为准";
        }
        if (priceMin != null && priceMax != null && priceMin.compareTo(priceMax) == 0) {
            return "¥" + priceMin.stripTrailingZeros().toPlainString();
        }
        if (priceMin != null && priceMax != null) {
            return "¥" + priceMin.stripTrailingZeros().toPlainString()
                    + "-" + priceMax.stripTrailingZeros().toPlainString();
        }
        return "¥" + (priceMin != null ? priceMin : priceMax).stripTrailingZeros().toPlainString() + " 起";
    }

    private String fallback(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String abbreviate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "…";
    }
}
