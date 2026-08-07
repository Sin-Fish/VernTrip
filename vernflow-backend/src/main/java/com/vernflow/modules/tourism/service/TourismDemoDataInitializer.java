package com.vernflow.modules.tourism.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.modules.tourism.domain.TourismResource;
import com.vernflow.modules.tourism.repository.TourismResourceRepository;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class TourismDemoDataInitializer implements ApplicationRunner {

    private static final String CITY = "杭州";
    private static final String SOURCE = "杭州文旅公开资料（演示整理）";
    private static final Map<String, GeoPoint> RESOURCE_COORDINATES = Map.ofEntries(
            Map.entry("中国杭州工艺美术博物馆", new GeoPoint("120.150114", "30.320335")),
            Map.entry("拱宸桥畔手作课堂", new GeoPoint("120.148541", "30.316932")),
            Map.entry("运河家常杭帮菜", new GeoPoint("120.146500", "30.317800")),
            Map.entry("湖滨亲子友好酒店", new GeoPoint("120.164750", "30.257250")),
            Map.entry("西湖环湖慢行段", new GeoPoint("120.157300", "30.252700")),
            Map.entry("清河坊宋韵夜游", new GeoPoint("120.171700", "30.237500")),
            Map.entry("中国丝绸博物馆", new GeoPoint("120.147500", "30.226400")),
            Map.entry("九溪烟树轻徒步", new GeoPoint("120.117500", "30.202600")),
            Map.entry("湖滨时令小馆", new GeoPoint("120.162900", "30.254700")),
            Map.entry("龙井茶园采制体验", new GeoPoint("120.103900", "30.223800")),
            Map.entry("小河直街午后散步", new GeoPoint("120.142500", "30.307400")),
            Map.entry("运河文化主题酒店", new GeoPoint("120.151300", "30.312500"))
    );

    private final TourismResourceRepository tourismResourceRepository;
    private final ObjectMapper objectMapper;

    public TourismDemoDataInitializer(
            TourismResourceRepository tourismResourceRepository,
            ObjectMapper objectMapper
    ) {
        this.tourismResourceRepository = tourismResourceRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (tourismResourceRepository.count() > 0) {
            return;
        }

        tourismResourceRepository.saveAll(List.of(
                resource("中国杭州工艺美术博物馆", TourismResource.Category.CULTURE, "拱墅区",
                        "从工艺器物认识杭州传统手艺，适合作为亲子文化体验的第一站。",
                        List.of("人文历史", "亲子友好", "室内"), List.of("亲子", "朋友"),
                        "拱墅区小河路", "09:00-16:30", 0, 0),
                resource("拱宸桥畔手作课堂", TourismResource.Category.EXPERIENCE, "拱墅区",
                        "围绕杭州传统工艺设置的小型手作体验，建议预留九十分钟。",
                        List.of("非遗体验", "亲子友好", "需预约"), List.of("亲子", "朋友", "情侣"),
                        "拱宸桥历史街区", "10:00-18:00", 120, 220),
                resource("运河家常杭帮菜", TourismResource.Category.FOOD, "拱墅区",
                        "提供适合家庭分享的杭帮菜，可提前备注儿童餐具和清淡口味。",
                        List.of("本地美食", "家庭用餐"), List.of("亲子", "朋友", "长辈"),
                        "桥西历史街区", "11:00-21:00", 80, 300),
                resource("湖滨亲子友好酒店", TourismResource.Category.LODGING, "上城区",
                        "靠近西湖湖滨步行区域，便于第二天减少跨区移动。",
                        List.of("亲子友好", "近景区"), List.of("亲子", "情侣"),
                        "西湖湖滨商圈", "全天", 680, 1280),
                resource("西湖环湖慢行段", TourismResource.Category.SCENIC, "西湖区",
                        "选取湖滨短距离慢行路段，保留拍照、休息和临时调整时间。",
                        List.of("自然风景", "城市漫步", "免费"), List.of("亲子", "朋友", "情侣", "长辈"),
                        "西湖景区", "全天开放", 0, 0),
                resource("清河坊宋韵夜游", TourismResource.Category.LEISURE, "上城区",
                        "适合傍晚至夜间轻松游览，可结合街区文化店铺灵活停留。",
                        List.of("夜游体验", "人文历史"), List.of("亲子", "朋友", "情侣"),
                        "清河坊历史街区", "全天开放", 0, 180),
                resource("中国丝绸博物馆", TourismResource.Category.CULTURE, "西湖区",
                        "以丝绸文化与服饰工艺为主题的室内场馆，适合作为雨天备选。",
                        List.of("人文历史", "室内", "免费"), List.of("亲子", "朋友", "长辈"),
                        "玉皇山路", "09:00-17:00", 0, 0),
                resource("九溪烟树轻徒步", TourismResource.Category.SCENIC, "西湖区",
                        "林荫与溪流相结合的轻徒步路线，应根据天气和同行人体力调整。",
                        List.of("自然风景", "轻徒步"), List.of("朋友", "情侣"),
                        "九溪路", "全天开放", 0, 0),
                resource("湖滨时令小馆", TourismResource.Category.FOOD, "上城区",
                        "位于湖滨步行范围内，减少午后跨区折返，提供当季杭州风味。",
                        List.of("本地美食", "近景区"), List.of("亲子", "朋友", "情侣"),
                        "湖滨商圈", "11:00-21:30", 100, 360),
                resource("龙井茶园采制体验", TourismResource.Category.EXPERIENCE, "西湖区",
                        "包含茶园讲解与基础制茶体验，活动受季节和天气影响。",
                        List.of("非遗体验", "自然风景", "需预约"), List.of("亲子", "朋友", "情侣"),
                        "龙井村", "09:00-17:00", 160, 360),
                resource("小河直街午后散步", TourismResource.Category.LEISURE, "拱墅区",
                        "以街巷、运河和社区店铺为主的自由漫步区域，适合放慢节奏。",
                        List.of("城市漫步", "人文历史", "免费"), List.of("朋友", "情侣", "长辈"),
                        "小河直街历史文化街区", "全天开放", 0, 0),
                resource("运河文化主题酒店", TourismResource.Category.LODGING, "拱墅区",
                        "位于运河片区的实惠住宿备选，适合预算调整后的方案。",
                        List.of("文化主题", "价格适中"), List.of("亲子", "朋友", "情侣"),
                        "运河沿线", "全天", 420, 760)
        ));
    }

    private TourismResource resource(
            String name,
            TourismResource.Category category,
            String district,
            String summary,
            List<String> tags,
            List<String> suitablePeople,
            String address,
            String openHours,
            int priceMin,
            int priceMax
    ) {
        TourismResource resource = TourismResource.published(
                CITY,
                name,
                category,
                district,
                summary,
                toJson(tags),
                toJson(suitablePeople),
                address,
                SOURCE,
                null,
                openHours,
                BigDecimal.valueOf(priceMin),
                BigDecimal.valueOf(priceMax)
        );
        GeoPoint point = RESOURCE_COORDINATES.get(name);
        if (point != null) {
            resource.updateCoordinates(new BigDecimal(point.longitude()), new BigDecimal(point.latitude()));
        }
        return resource;
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize tourism demo data.", exception);
        }
    }

    private record GeoPoint(String longitude, String latitude) {
    }
}
