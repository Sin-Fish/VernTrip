package com.vernflow.modules.tourism.service;

import com.vernflow.common.config.OpenClawProperties;
import com.vernflow.common.exception.BusinessException;
import com.vernflow.modules.openclaw.service.OpenClawGatewayClient;
import com.vernflow.modules.openclaw.service.OpenClawSessionContextService;
import com.vernflow.modules.tourism.dto.MerchantContentGenerateRequest;
import com.vernflow.modules.tourism.dto.MerchantContentGenerateResponse;
import com.vernflow.modules.tourism.dto.MerchantProductResponse;
import com.vernflow.modules.tourism.dto.MerchantResourceResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class MerchantContentService {

    private static final String CONTENT_INSTRUCTIONS = """
            你是 VernTrip 商家端的文旅内容运营助手。只允许使用提示词中给出的已核验事实，不得编造价格、地点、开放时间、余量、服务承诺或奖项。
            根据指定渠道和表达风格生成一份可人工编辑的中文营销草稿。内容应自然、具体，并在需要时包含预约或容量提示。
            只输出最终文案正文，不要解释生成过程，不要使用 Markdown 代码块。
            """;

    private final OpenClawProperties openClawProperties;
    private final OpenClawSessionContextService sessionContextService;
    private final OpenClawGatewayClient gatewayClient;
    private final MerchantResourceService resourceService;
    private final MerchantProductService productService;

    public MerchantContentService(
            OpenClawProperties openClawProperties,
            OpenClawSessionContextService sessionContextService,
            OpenClawGatewayClient gatewayClient,
            MerchantResourceService resourceService,
            MerchantProductService productService
    ) {
        this.openClawProperties = openClawProperties;
        this.sessionContextService = sessionContextService;
        this.gatewayClient = gatewayClient;
        this.resourceService = resourceService;
        this.productService = productService;
    }

    @Transactional
    public MerchantContentGenerateResponse generate(
            Long merchantUserId,
            MerchantContentGenerateRequest request
    ) {
        if (!openClawProperties.primaryEnabled()) {
            throw new BusinessException("当前未启用 OpenClaw 主引擎，无法生成真实营销内容。");
        }
        SourceFacts source = request.sourceType() == MerchantContentGenerateRequest.SourceType.PRODUCT
                ? productFacts(merchantUserId, request.sourceId())
                : resourceFacts(request.sourceId());
        OpenClawSessionContextService.BoundSession session = sessionContextService.bind(
                merchantUserId,
                null,
                false,
                List.of()
        );
        String prompt = buildPrompt(source, request);
        try {
            OpenClawGatewayClient.ShadowResponse response = gatewayClient.createResponse(
                    session.sessionKey(),
                    prompt,
                    CONTENT_INSTRUCTIONS
            );
            return new MerchantContentGenerateResponse(
                    source.name(),
                    request.channel().trim(),
                    response.text().trim(),
                    "OPENCLAW",
                    source.facts(),
                    LocalDateTime.now()
            );
        } catch (OpenClawGatewayClient.GatewayCallException exception) {
            throw new BusinessException("OpenClaw 内容生成暂不可用，请检查网关状态后重试。");
        }
    }

    private SourceFacts resourceFacts(Long resourceId) {
        MerchantResourceResponse resource = resourceService.listResources().stream()
                .filter(item -> item.id().equals(resourceId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("用于生成内容的文旅资源不存在。"));
        List<String> facts = new ArrayList<>();
        facts.add("名称：" + resource.name());
        facts.add("类型与区域：" + resource.categoryLabel() + "，" + resource.district());
        facts.add("真实地点：" + resource.placeName() + "，" + resource.address());
        facts.add("体验说明：" + resource.summary());
        facts.add("开放时间：" + fallback(resource.openHours(), "待商家确认"));
        facts.add("价格区间：" + priceRange(resource.priceMin(), resource.priceMax()));
        facts.add("标签：" + String.join("、", resource.tags()));
        if (resource.reservationRequired()) {
            facts.add("预约状态：" + resource.availabilityLabel() + "，当前余量 "
                    + resource.availableCapacity() + "/" + resource.dailyCapacity());
        } else {
            facts.add("预约状态：无需预约");
        }
        return new SourceFacts(resource.name(), facts);
    }

    private SourceFacts productFacts(Long merchantUserId, Long productId) {
        MerchantProductResponse product = productService.getProduct(merchantUserId, productId);
        List<String> facts = new ArrayList<>();
        facts.add("产品名称：" + product.name());
        facts.add("目标客群：" + product.targetAudience());
        facts.add("行程时长：" + product.durationDays() + " 天");
        facts.add("产品价格：¥" + product.price().stripTrailingZeros().toPlainString());
        facts.add("产品说明：" + product.description());
        facts.add("产品亮点：" + String.join("、", product.highlights()));
        facts.add("包含资源：" + product.resources().stream()
                .map(MerchantProductResponse.ResourceSummary::name)
                .reduce((left, right) -> left + "、" + right)
                .orElse("未配置"));
        return new SourceFacts(product.name(), facts);
    }

    private String buildPrompt(SourceFacts source, MerchantContentGenerateRequest request) {
        String goal = StringUtils.hasText(request.goal()) ? request.goal().trim() : "吸引目标游客了解并咨询";
        return """
                请依据以下事实生成营销内容。
                发布渠道：%s
                表达风格：%s
                运营目标：%s

                可信事实：
                %s
                """.formatted(
                request.channel().trim(),
                request.tone().trim(),
                goal,
                source.facts().stream().map(item -> "- " + item).reduce((left, right) -> left + "\n" + right).orElse("")
        );
    }

    private String fallback(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String priceRange(java.math.BigDecimal minimum, java.math.BigDecimal maximum) {
        java.math.BigDecimal min = minimum == null ? java.math.BigDecimal.ZERO : minimum;
        java.math.BigDecimal max = maximum == null ? min : maximum;
        if (min.signum() == 0 && max.signum() == 0) {
            return "免费或以现场公示为准";
        }
        if (min.compareTo(max) == 0) {
            return "¥" + min.stripTrailingZeros().toPlainString();
        }
        return "¥" + min.stripTrailingZeros().toPlainString() + "–¥" + max.stripTrailingZeros().toPlainString();
    }

    private record SourceFacts(String name, List<String> facts) {
    }
}
