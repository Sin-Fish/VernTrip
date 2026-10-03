package com.vernflow.modules.tourism.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.modules.tourism.domain.TourismPlace;
import com.vernflow.modules.tourism.domain.TourismResource;
import com.vernflow.modules.tourism.dto.MerchantProductResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VideoPromptBuilderTest {

    private VideoPromptBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new VideoPromptBuilder(new ObjectMapper());
    }

    @Test
    void buildsDifferentiatedScenesPerCategory() {
        String scenic = promptFor(TourismResource.Category.SCENIC);
        String food = promptFor(TourismResource.Category.FOOD);
        assertThat(scenic).contains("自然风光");
        assertThat(food).contains("美食餐饮");
        assertThat(scenic).isNotEqualTo(food);
    }

    @Test
    void includesVerifiedFactsAndStyleSections() {
        String prompt = promptFor(TourismResource.Category.CULTURE);
        assertThat(prompt).contains("【画面主体】灵隐寺（杭州·西湖区）");
        assertThat(prompt).contains("开放时间08:00-17:00");
        assertThat(prompt).contains("¥30-45");
        assertThat(prompt).contains("禅意");
        assertThat(prompt).contains("【镜头语言】");
        assertThat(prompt).contains("【风格要求】写实摄影风格");
        assertThat(prompt).contains("无文字、无水印");
    }

    @Test
    void usesCustomHintWhenProvided() {
        String prompt = builder.buildResourcePrompt(resource(), "突出雪景中的古寺静谧感");
        assertThat(prompt).contains("【补充要求】突出雪景中的古寺静谧感");
        assertThat(prompt).doesNotContain("突出当地特色与沉浸式旅行氛围");
    }

    @Test
    void truncatesLongPromptsToKlingLimit() {
        String longSummary = "很长的介绍".repeat(300);
        TourismPlace place = TourismPlace.verified(
                "杭州", "测试地点", "西湖区", "测试路1号", "POI-1",
                BigDecimal.valueOf(120.1), BigDecimal.valueOf(30.2), "测试来源"
        );
        TourismResource resource = TourismResource.published(
                place, "长介绍资源", TourismResource.Category.SCENIC, longSummary,
                "[\"标签\"]", "[]", "测试来源", "https://example.com",
                "08:00-17:00", BigDecimal.valueOf(30), BigDecimal.valueOf(45)
        );
        String prompt = builder.buildResourcePrompt(resource, null);
        assertThat(prompt).hasSizeLessThanOrEqualTo(2500);
    }

    @Test
    void buildsProductPromptWithHighlightsAndPrice() {
        MerchantProductResponse product = new MerchantProductResponse(
                1L, "杭州亲子非遗两日游", "亲子家庭", 2, new BigDecimal("1288.00"),
                "两天一夜的非遗主题亲子行程。",
                List.of("亲手体验传统扎染", "夜游古镇灯光秀"),
                List.of(new MerchantProductResponse.ResourceSummary(
                        5L, "手工艺活态馆", "EXPERIENCE", "体验", "PUBLISHED", true
                )),
                "DRAFT", "草稿", LocalDateTime.now()
        );
        String prompt = builder.buildProductPrompt(product, null);
        assertThat(prompt).contains("【画面主体】杭州亲子非遗两日游（亲子家庭，2 天行程）");
        assertThat(prompt).contains("亲手体验传统扎染，夜游古镇灯光秀");
        assertThat(prompt).contains("¥1288");
        assertThat(prompt).contains("手工艺活态馆");
    }

    private String promptFor(TourismResource.Category category) {
        TourismPlace place = TourismPlace.verified(
                "杭州", "灵隐寺", "西湖区", "法云弄1号", "POI-1",
                BigDecimal.valueOf(120.1), BigDecimal.valueOf(30.2), "高德"
        );
        TourismResource resource = TourismResource.published(
                place, "灵隐寺", category, "千年古刹，香火鼎盛，古木参天。",
                "[\"禅意\",\"古迹\",\"祈福\"]", "[]", "高德", "https://example.com",
                "08:00-17:00", BigDecimal.valueOf(30), BigDecimal.valueOf(45)
        );
        return builder.buildResourcePrompt(resource, null);
    }

    private TourismResource resource() {
        return TourismResource.published(
                TourismPlace.verified(
                        "杭州", "灵隐寺", "西湖区", "法云弄1号", "POI-1",
                        BigDecimal.valueOf(120.1), BigDecimal.valueOf(30.2), "高德"
                ),
                "灵隐寺", TourismResource.Category.CULTURE, "千年古刹，香火鼎盛。",
                "[\"禅意\",\"古迹\"]", "[]", "高德", "https://example.com",
                "08:00-17:00", BigDecimal.valueOf(30), BigDecimal.valueOf(45)
        );
    }
}
