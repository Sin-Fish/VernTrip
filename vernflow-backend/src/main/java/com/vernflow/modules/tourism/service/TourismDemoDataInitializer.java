package com.vernflow.modules.tourism.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.modules.tourism.domain.TourismPlace;
import com.vernflow.modules.tourism.domain.TourismResource;
import com.vernflow.modules.tourism.repository.TourismPlaceRepository;
import com.vernflow.modules.tourism.repository.TourismResourceRepository;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
@Order(10)
public class TourismDemoDataInitializer implements ApplicationRunner {

    private static final String CITY = "杭州";
    private static final String SOURCE = "杭州文旅公开资料（演示整理）";
    private static final Map<String, DemoPlace> RESOURCE_PLACES = Map.ofEntries(
            Map.entry("中国杭州工艺美术博物馆", new DemoPlace("杭州工艺美术博物馆", "拱墅区", "小河路334号", "B023B0I02U", "120.136930", "30.315645")),
            Map.entry("拱宸桥畔手作课堂", new DemoPlace("杭州工艺美术博物馆", "拱墅区", "小河路334号", "B023B0I02U", "120.136930", "30.315645")),
            Map.entry("运河家常杭帮菜", new DemoPlace("拱宸桥西历史文化街区", "拱墅区", "桥西直街1号（拱宸桥西）", "B0FFFFZRBD", "120.137752", "30.316781")),
            Map.entry("湖滨亲子友好酒店", new DemoPlace("杭州友好饭店", "上城区", "平海路53号", "B023B02XNQ", "120.166521", "30.253702")),
            Map.entry("西湖环湖慢行段", new DemoPlace("杭州西湖风景名胜区-湖滨公园", "上城区", "龙井路1号杭州西湖风景名胜区内（东北角）", "B023B05MLT", "120.158818", "30.256583")),
            Map.entry("清河坊宋韵夜游", new DemoPlace("清河坊历史文化特色街区", "上城区", "河坊街180号", "B023B07SJZ", "120.169899", "30.239827")),
            Map.entry("中国丝绸博物馆", new DemoPlace("中国丝绸博物馆", "西湖区", "玉皇山路73-1号（近南山路）", "B023B09JLL", "120.151292", "30.222870")),
            Map.entry("九溪烟树轻徒步", new DemoPlace("九溪烟树", "西湖区", "龙井村杭州西湖风景名胜区内（近九溪路）", "B0FFHMH4J8", "120.113350", "30.202395")),
            Map.entry("湖滨时令小馆", new DemoPlace("杭州酒家（延安路店）", "上城区", "延安路205号1-3层", "B023B1994H", "120.164014", "30.251744")),
            Map.entry("龙井茶园采制体验", new DemoPlace("中国茶叶博物馆（龙井馆区）", "西湖区", "满觉陇路翁家山268号", "B023B09JN4", "120.113539", "30.219662")),
            Map.entry("小河直街午后散步", new DemoPlace("小河历史文化街区", "拱墅区", "小河直街51号", "B0FFFWQ8DS", "120.135337", "30.308302")),
            Map.entry("运河文化主题酒店", new DemoPlace("杭州运河祈利酒店", "拱墅区", "丽水路与大兜路交叉口西80米", "B0HBBO6XGX", "120.145661", "30.300005"))
    );

    private final TourismResourceRepository tourismResourceRepository;
    private final TourismPlaceRepository tourismPlaceRepository;
    private final ObjectMapper objectMapper;

    public TourismDemoDataInitializer(
            TourismResourceRepository tourismResourceRepository,
            TourismPlaceRepository tourismPlaceRepository,
            ObjectMapper objectMapper
    ) {
        this.tourismResourceRepository = tourismResourceRepository;
        this.tourismPlaceRepository = tourismPlaceRepository;
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
                        "09:00-16:30", 0, 0),
                resource("拱宸桥畔手作课堂", TourismResource.Category.EXPERIENCE, "拱墅区",
                        "围绕杭州传统工艺设置的小型手作体验，建议预留九十分钟。",
                        List.of("非遗体验", "亲子友好", "需预约"), List.of("亲子", "朋友", "情侣"),
                        "10:00-18:00", 120, 220),
                resource("运河家常杭帮菜", TourismResource.Category.FOOD, "拱墅区",
                        "提供适合家庭分享的杭帮菜，可提前备注儿童餐具和清淡口味。",
                        List.of("本地美食", "家庭用餐"), List.of("亲子", "朋友", "长辈"),
                        "11:00-21:00", 80, 300),
                resource("湖滨亲子友好酒店", TourismResource.Category.LODGING, "上城区",
                        "靠近西湖湖滨步行区域，便于第二天减少跨区移动。",
                        List.of("亲子友好", "近景区"), List.of("亲子", "情侣"),
                        "全天", 680, 1280),
                resource("西湖环湖慢行段", TourismResource.Category.SCENIC, "西湖区",
                        "选取湖滨短距离慢行路段，保留拍照、休息和临时调整时间。",
                        List.of("自然风景", "城市漫步", "免费"), List.of("亲子", "朋友", "情侣", "长辈"),
                        "全天开放", 0, 0),
                resource("清河坊宋韵夜游", TourismResource.Category.LEISURE, "上城区",
                        "适合傍晚至夜间轻松游览，可结合街区文化店铺灵活停留。",
                        List.of("夜游体验", "人文历史"), List.of("亲子", "朋友", "情侣"),
                        "全天开放", 0, 180),
                resource("中国丝绸博物馆", TourismResource.Category.CULTURE, "西湖区",
                        "以丝绸文化与服饰工艺为主题的室内场馆，适合作为雨天备选。",
                        List.of("人文历史", "室内", "免费"), List.of("亲子", "朋友", "长辈"),
                        "09:00-17:00", 0, 0),
                resource("九溪烟树轻徒步", TourismResource.Category.SCENIC, "西湖区",
                        "林荫与溪流相结合的轻徒步路线，应根据天气和同行人体力调整。",
                        List.of("自然风景", "轻徒步"), List.of("朋友", "情侣"),
                        "全天开放", 0, 0),
                resource("湖滨时令小馆", TourismResource.Category.FOOD, "上城区",
                        "位于湖滨步行范围内，减少午后跨区折返，提供当季杭州风味。",
                        List.of("本地美食", "近景区"), List.of("亲子", "朋友", "情侣"),
                        "11:00-21:30", 100, 360),
                resource("龙井茶园采制体验", TourismResource.Category.EXPERIENCE, "西湖区",
                        "包含茶园讲解与基础制茶体验，活动受季节和天气影响。",
                        List.of("非遗体验", "自然风景", "需预约"), List.of("亲子", "朋友", "情侣"),
                        "09:00-17:00", 160, 360),
                resource("小河直街午后散步", TourismResource.Category.LEISURE, "拱墅区",
                        "以街巷、运河和社区店铺为主的自由漫步区域，适合放慢节奏。",
                        List.of("城市漫步", "人文历史", "免费"), List.of("朋友", "情侣", "长辈"),
                        "全天开放", 0, 0),
                resource("运河文化主题酒店", TourismResource.Category.LODGING, "拱墅区",
                        "位于运河片区的实惠住宿备选，适合预算调整后的方案。",
                        List.of("文化主题", "价格适中"), List.of("亲子", "朋友", "情侣"),
                        "全天", 420, 760)
        ));
    }

    private TourismResource resource(
            String name,
            TourismResource.Category category,
            String district,
            String summary,
            List<String> tags,
            List<String> suitablePeople,
            String openHours,
            int priceMin,
            int priceMax
    ) {
        DemoPlace demoPlace = RESOURCE_PLACES.get(name);
        TourismPlace place = tourismPlaceRepository.findByAmapPoiId(demoPlace.amapPoiId())
                .orElseGet(() -> tourismPlaceRepository.save(TourismPlace.verified(
                        CITY,
                        demoPlace.name(),
                        demoPlace.district(),
                        demoPlace.address(),
                        demoPlace.amapPoiId(),
                        new BigDecimal(demoPlace.longitude()),
                        new BigDecimal(demoPlace.latitude()),
                        "高德 POI（2026-08-10 核验）"
                )));
        TourismResource resource = TourismResource.published(
                place,
                name,
                category,
                summary,
                toJson(tags),
                toJson(suitablePeople),
                SOURCE,
                null,
                openHours,
                BigDecimal.valueOf(priceMin),
                BigDecimal.valueOf(priceMax)
        );
        return resource;
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize tourism demo data.", exception);
        }
    }

    private record DemoPlace(
            String name,
            String district,
            String address,
            String amapPoiId,
            String longitude,
            String latitude
    ) {
    }
}
