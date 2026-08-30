package com.vernflow.modules.tourism.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.modules.tourism.domain.TourismProduct;
import com.vernflow.modules.tourism.domain.TourismResource;
import com.vernflow.modules.tourism.repository.TourismProductRepository;
import com.vernflow.modules.tourism.repository.TourismResourceRepository;
import com.vernflow.modules.user.domain.AppUser;
import com.vernflow.modules.user.repository.AppUserRepository;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Order(20)
public class TourismProductDemoDataInitializer implements ApplicationRunner {

    private final TourismProductRepository productRepository;
    private final TourismResourceRepository resourceRepository;
    private final AppUserRepository appUserRepository;
    private final ObjectMapper objectMapper;

    public TourismProductDemoDataInitializer(
            TourismProductRepository productRepository,
            TourismResourceRepository resourceRepository,
            AppUserRepository appUserRepository,
            ObjectMapper objectMapper
    ) {
        this.productRepository = productRepository;
        this.resourceRepository = resourceRepository;
        this.appUserRepository = appUserRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (productRepository.count() > 0) {
            return;
        }
        AppUser merchant = appUserRepository.findByUsernameIgnoreCase("merchant").orElse(null);
        if (merchant == null) {
            return;
        }
        Map<String, TourismResource> resources = resourceRepository.findAll().stream()
                .collect(Collectors.toMap(TourismResource::getName, Function.identity()));
        if (resources.size() < 6) {
            return;
        }

        TourismProduct family = product(
                merchant.getId(),
                "运河非遗亲子慢游 · 两日",
                "亲子家庭",
                2,
                699,
                "以运河文化和手作参与为主线，控制跨区移动，兼顾儿童节奏与家庭用餐。",
                List.of("非遗手作", "雨天可参与", "亲子友好", "运河文化"),
                resources,
                "中国杭州工艺美术博物馆",
                "拱宸桥畔手作课堂",
                "运河家常杭帮菜",
                "运河文化主题酒店"
        );
        family.changeStatus(TourismProduct.Status.PUBLISHED);

        TourismProduct night = product(
                merchant.getId(),
                "宋韵夜游 · 城市半日",
                "年轻游客与情侣",
                1,
                268,
                "从湖滨慢行到清河坊夜游，以轻量文化体验和本地餐饮连接城市夜间场景。",
                List.of("宋韵街区", "城市夜游", "本地风味"),
                resources,
                "西湖环湖慢行段",
                "湖滨时令小馆",
                "清河坊宋韵夜游"
        );
        night.changeStatus(TourismProduct.Status.PUBLISHED);

        TourismProduct tea = product(
                merchant.getId(),
                "龙井茶事与九溪轻行 · 一日",
                "文化体验与轻户外游客",
                1,
                399,
                "把茶文化体验和九溪轻徒步组合成一日产品，正式发布前需补充天气备选方案。",
                List.of("茶文化", "轻徒步", "季节限定"),
                resources,
                "龙井茶园采制体验",
                "九溪烟树轻徒步"
        );

        productRepository.saveAll(List.of(family, night, tea));
    }

    private TourismProduct product(
            Long merchantUserId,
            String name,
            String audience,
            int days,
            int price,
            String description,
            List<String> highlights,
            Map<String, TourismResource> resources,
            String... resourceNames
    ) {
        List<TourismResource> selectedResources = java.util.Arrays.stream(resourceNames)
                .map(resources::get)
                .toList();
        return TourismProduct.draft(
                merchantUserId,
                name,
                audience,
                days,
                BigDecimal.valueOf(price),
                description,
                writeList(highlights),
                selectedResources
        );
    }

    private String writeList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize tourism product demo data.", exception);
        }
    }
}
