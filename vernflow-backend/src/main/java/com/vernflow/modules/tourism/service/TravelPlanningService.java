package com.vernflow.modules.tourism.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.common.config.OpenClawProperties;
import com.vernflow.common.exception.BusinessException;
import com.vernflow.modules.tourism.domain.TourismResource;
import com.vernflow.modules.tourism.domain.TravelPlan;
import com.vernflow.modules.tourism.domain.TravelPlanItem;
import com.vernflow.modules.tourism.domain.TripFeedback;
import com.vernflow.modules.tourism.domain.TripRequest;
import com.vernflow.modules.tourism.dto.TourismResourceResponse;
import com.vernflow.modules.tourism.dto.TravelPlanAdjustRequest;
import com.vernflow.modules.tourism.dto.TravelPlanCreateRequest;
import com.vernflow.modules.tourism.dto.TravelPlanResponse;
import com.vernflow.modules.tourism.repository.TourismResourceRepository;
import com.vernflow.modules.tourism.repository.TravelPlanItemRepository;
import com.vernflow.modules.tourism.repository.TravelPlanRepository;
import com.vernflow.modules.tourism.repository.TripFeedbackRepository;
import com.vernflow.modules.tourism.repository.TripRequestRepository;
import com.vernflow.modules.user.service.UserAuthService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TravelPlanningService {

    private static final String SUPPORTED_CITY = "杭州";
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("M月d日");
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final UserAuthService userAuthService;
    private final TourismResourceRepository tourismResourceRepository;
    private final TripRequestRepository tripRequestRepository;
    private final TravelPlanRepository travelPlanRepository;
    private final TravelPlanItemRepository travelPlanItemRepository;
    private final TripFeedbackRepository tripFeedbackRepository;
    private final ObjectMapper objectMapper;
    private final OpenClawItineraryService openClawItineraryService;
    private final OpenClawProperties openClawProperties;

    public TravelPlanningService(
            UserAuthService userAuthService,
            TourismResourceRepository tourismResourceRepository,
            TripRequestRepository tripRequestRepository,
            TravelPlanRepository travelPlanRepository,
            TravelPlanItemRepository travelPlanItemRepository,
            TripFeedbackRepository tripFeedbackRepository,
            ObjectMapper objectMapper,
            OpenClawItineraryService openClawItineraryService,
            OpenClawProperties openClawProperties
    ) {
        this.userAuthService = userAuthService;
        this.tourismResourceRepository = tourismResourceRepository;
        this.tripRequestRepository = tripRequestRepository;
        this.travelPlanRepository = travelPlanRepository;
        this.travelPlanItemRepository = travelPlanItemRepository;
        this.tripFeedbackRepository = tripFeedbackRepository;
        this.objectMapper = objectMapper;
        this.openClawItineraryService = openClawItineraryService;
        this.openClawProperties = openClawProperties;
    }

    @Transactional(readOnly = true)
    public List<TourismResourceResponse> listResources(String city) {
        String targetCity = StringUtils.hasText(city) ? city.trim() : SUPPORTED_CITY;
        return publishedResources(targetCity).stream()
                .map(this::toResourceResponse)
                .toList();
    }

    @Transactional
    public TravelPlanResponse createPlan(Long userId, TravelPlanCreateRequest request) {
        userAuthService.requireUser(userId);
        TripRequest tripRequest = tripRequestRepository.save(TripRequest.create(
                userId,
                request.destination().trim(),
                trimToNull(request.departure()),
                request.date(),
                request.days(),
                request.travelers().trim(),
                request.budget().setScale(2, RoundingMode.HALF_UP),
                request.pace().trim(),
                writeList(request.interests()),
                trimToNull(request.notes())
        ));
        return generatePlan(tripRequest, null, 1);
    }

    @Transactional(readOnly = true)
    public TravelPlanResponse getPlan(Long userId, Long planId) {
        return toResponse(requireOwnedPlan(userId, planId));
    }

    @Transactional
    public TravelPlanResponse adjustPlan(Long userId, Long planId, TravelPlanAdjustRequest adjustment) {
        TravelPlan previousPlan = requireOwnedPlan(userId, planId);
        TripRequest previousRequest = tripRequestRepository.findById(previousPlan.getTripRequestId())
                .orElseThrow(() -> new BusinessException("原始旅行需求不存在。"));

        String type = adjustment.type().trim().toLowerCase(Locale.ROOT);
        BigDecimal budget = previousRequest.getBudget();
        String pace = previousRequest.getPace();
        String notes = previousRequest.getNotes();

        switch (type) {
            case "budget" -> budget = budget.subtract(BigDecimal.valueOf(500))
                    .max(BigDecimal.valueOf(1200));
            case "pace" -> pace = "轻松一些";
            case "hotel" -> notes = appendNote(notes, "希望住宿更实惠。");
            case "custom" -> {
                if (!StringUtils.hasText(adjustment.note())) {
                    throw new BusinessException("请填写希望调整的内容。");
                }
            }
            default -> throw new BusinessException("不支持的调整类型：" + adjustment.type());
        }

        if (StringUtils.hasText(adjustment.note())) {
            notes = appendNote(notes, adjustment.note().trim());
        }

        TripRequest nextRequest = tripRequestRepository.save(TripRequest.create(
                userId,
                previousRequest.getDestination(),
                previousRequest.getDepartureCity(),
                previousRequest.getTravelDate(),
                previousRequest.getDays(),
                previousRequest.getTravelerType(),
                budget,
                pace,
                previousRequest.getInterestsJson(),
                notes
        ));
        return generatePlan(nextRequest, previousPlan.getId(), previousPlan.getVersion() + 1);
    }

    @Transactional
    public TravelPlanResponse savePlan(Long userId, Long planId) {
        TravelPlan plan = requireOwnedPlan(userId, planId);
        plan.markSaved();
        travelPlanRepository.save(plan);
        if (!tripFeedbackRepository.existsByUserIdAndPlanIdAndType(
                userId,
                planId,
                TripFeedback.Type.COLLECTED
        )) {
            tripFeedbackRepository.save(TripFeedback.create(
                    userId,
                    planId,
                    TripFeedback.Type.COLLECTED,
                    "游客收藏行程"
            ));
        }
        return toResponse(plan);
    }

    @Transactional
    public void removeSavedPlan(Long userId, Long planId) {
        TravelPlan plan = requireOwnedPlan(userId, planId);
        plan.markDraft();
        travelPlanRepository.save(plan);
        tripFeedbackRepository.deleteByUserIdAndPlanIdAndType(
                userId,
                planId,
                TripFeedback.Type.COLLECTED
        );
    }

    @Transactional(readOnly = true)
    public List<TravelPlanResponse> listSavedPlans(Long userId) {
        userAuthService.requireUser(userId);
        return travelPlanRepository
                .findAllByUserIdAndStatusOrderByUpdatedAtDesc(userId, TravelPlan.Status.SAVED)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private TravelPlanResponse generatePlan(TripRequest request, Long parentPlanId, int version) {
        List<TourismResource> resources = publishedResources(request.getDestination());
        if (resources.isEmpty()) {
            throw new BusinessException("当前首版仅支持杭州的一至两日行程规划。");
        }

        Map<String, TourismResource> resourcesByName = resources.stream()
                .collect(Collectors.toMap(TourismResource::getName, Function.identity()));
        OpenClawItineraryService.AgentItinerary agentItinerary = openClawItineraryService
                .generate(request.getUserId(), request, resources)
                .orElse(null);
        List<PlannedStop> stops = agentItinerary == null
                ? plannedStops(request)
                : agentItinerary.stops().stream()
                        .map(stop -> new PlannedStop(
                                stop.day(),
                                stop.sequence(),
                                stop.time(),
                                stop.label(),
                                stop.resourceName(),
                                stop.description(),
                                stop.tag(),
                                stop.cost()
                        ))
                        .toList();
        String generationEngine = agentItinerary != null
                ? "openclaw"
                : openClawProperties.primaryEnabled() ? "local-fallback" : "local";
        BigDecimal transportation = transportationBudget(request.getBudget());
        BigDecimal estimatedBudget = stops.stream()
                .map(PlannedStop::cost)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(transportation);

        TravelPlan plan = travelPlanRepository.save(TravelPlan.create(
                request.getUserId(),
                request.getId(),
                parentPlanId,
                version,
                agentItinerary == null ? planTitle(request) : agentItinerary.title(),
                agentItinerary == null ? planSummary(request) : agentItinerary.summary(),
                agentItinerary == null ? (version == 1 ? 92 : 94) : agentItinerary.matchScore(),
                estimatedBudget,
                generationEngine
        ));

        List<TravelPlanItem> items = stops.stream()
                .map(stop -> TravelPlanItem.create(
                        plan.getId(),
                        requireResource(resourcesByName, stop.resourceName()),
                        stop.day(),
                        stop.sequence(),
                        stop.time(),
                        stop.label(),
                        stop.description(),
                        stop.tag(),
                        stop.cost()
                ))
                .toList();
        travelPlanItemRepository.saveAll(items);
        return toResponse(plan);
    }

    private List<PlannedStop> plannedStops(TripRequest request) {
        boolean family = request.getTravelerType().contains("亲子");
        boolean relaxed = request.getPace().contains("轻松");
        boolean lowerBudget = request.getBudget().compareTo(BigDecimal.valueOf(2000)) <= 0;
        boolean affordableHotel = lowerBudget || contains(request.getNotes(), "住宿更实惠");

        BigDecimal firstMeal = money(lowerBudget ? 160 : 260);
        BigDecimal activity = money(lowerBudget ? 120 : 180);
        BigDecimal secondMeal = money(lowerBudget ? 180 : 320);
        BigDecimal nightVisit = money(lowerBudget ? 80 : 160);
        BigDecimal hotel = affordableHotel
                ? request.getBudget().multiply(BigDecimal.valueOf(0.24))
                        .max(BigDecimal.valueOf(420))
                        .setScale(0, RoundingMode.HALF_UP)
                : request.getBudget().multiply(BigDecimal.valueOf(0.34))
                        .setScale(0, RoundingMode.HALF_UP);

        List<PlannedStop> firstDay = new ArrayList<>();
        firstDay.add(new PlannedStop(1, 1, "09:30", "慢一点出发",
                "中国杭州工艺美术博物馆",
                family
                        ? "从一件器物开始认识杭州手艺，儿童互动区域适合作为行程热身。"
                        : "从传统工艺切入杭州城市文化，为后续街区漫步建立背景。",
                "文化体验", BigDecimal.ZERO));
        firstDay.add(new PlannedStop(1, 2, "12:15", "午餐",
                "运河家常杭帮菜",
                family
                        ? "提前备注儿童餐具和清淡口味，午间不安排跨区赶路。"
                        : "选择当季杭帮菜，餐后在运河边短暂休息。",
                "本地美食", firstMeal));
        firstDay.add(new PlannedStop(1, 3, "14:30", "下午的记忆",
                "拱宸桥畔手作课堂",
                relaxed
                        ? "预留九十分钟完成一件小作品，结束后可在街区自由停留。"
                        : "手作之后增加一段街区寻访，让下午体验更充实。",
                "非遗体验", activity));

        if (request.getDays() == 1) {
            firstDay.add(new PlannedStop(1, 4, "18:30", "傍晚收尾",
                    "清河坊宋韵夜游",
                    "晚餐后按体力自由游览街区，不设置必须完成的打卡点。",
                    "人文历史", nightVisit));
            return firstDay;
        }

        firstDay.add(new PlannedStop(1, 4, "18:30", "今晚住下",
                affordableHotel ? "运河文化主题酒店" : "湖滨亲子友好酒店",
                affordableHotel
                        ? "选择交通便利、价格适中的住宿，把更多预算留给体验项目。"
                        : "放下行李后不安排远距离移动，为第二天保留体力。",
                "住宿", hotel));

        List<PlannedStop> allStops = new ArrayList<>(firstDay);
        allStops.add(new PlannedStop(2, 1, "09:00", "湖边的早晨",
                "西湖环湖慢行段",
                relaxed
                        ? "只走一小段，沿途留出拍照和休息时间。"
                        : "从湖滨出发，串联两至三个停留点。",
                "自然风景", BigDecimal.ZERO));
        allStops.add(new PlannedStop(2, 2, "12:00", "午餐",
                "湖滨时令小馆",
                "选择距离下一站较近的餐厅，避免午后长距离折返。",
                "本地美食", secondMeal));
        allStops.add(new PlannedStop(2, 3, "15:00", "把故事带回家",
                "清河坊宋韵夜游",
                family
                        ? "选择一件有明确工艺说明的小物作为旅行纪念。"
                        : "留出自由探索时间，按兴趣进入不同店铺。",
                "人文历史", nightVisit));
        return allStops;
    }

    private TravelPlanResponse toResponse(TravelPlan plan) {
        TripRequest request = tripRequestRepository.findById(plan.getTripRequestId())
                .orElseThrow(() -> new BusinessException("旅行需求不存在。"));
        List<TravelPlanItem> items = travelPlanItemRepository
                .findAllByPlanIdOrderByDayNumberAscSequenceNoAsc(plan.getId());
        Map<Long, TourismResource> resourceMap = tourismResourceRepository.findAllById(
                        items.stream()
                                .map(TravelPlanItem::getResourceId)
                                .filter(Objects::nonNull)
                                .distinct()
                                .toList()
                ).stream()
                .collect(Collectors.toMap(TourismResource::getId, Function.identity()));

        Map<Integer, List<TravelPlanItem>> itemsByDay = items.stream()
                .collect(Collectors.groupingBy(
                        TravelPlanItem::getDayNumber,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<TravelPlanResponse.DayPlan> days = itemsByDay.entrySet().stream()
                .map(entry -> new TravelPlanResponse.DayPlan(
                        entry.getKey(),
                        request.getTravelDate().plusDays(entry.getKey() - 1L).format(DISPLAY_DATE),
                        dayTitle(entry.getKey()),
                        entry.getValue().stream()
                                .map(item -> toPlanItem(item, resourceMap.get(item.getResourceId())))
                                .toList()
                ))
                .toList();

        BigDecimal stay = sumCosts(items, "住宿");
        BigDecimal food = sumCosts(items, "本地美食");
        BigDecimal activity = sumCosts(items, "非遗体验", "人文历史");
        BigDecimal transportation = transportationBudget(request.getBudget());
        BigDecimal itemTotal = items.stream()
                .map(TravelPlanItem::getEstimatedCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal reserve = request.getBudget()
                .subtract(itemTotal)
                .subtract(transportation)
                .max(BigDecimal.ZERO);

        List<TourismResourceResponse> sources = items.stream()
                .map(TravelPlanItem::getResourceId)
                .filter(Objects::nonNull)
                .distinct()
                .map(resourceMap::get)
                .filter(Objects::nonNull)
                .map(this::toResourceResponse)
                .toList();

        return new TravelPlanResponse(
                plan.getId(),
                plan.getParentPlanId(),
                plan.getVersion(),
                plan.getTitle(),
                plan.getCreatedAt().format(DISPLAY_DATE),
                statusLabel(plan.getStatus()),
                plan.getGenerationEngine(),
                new TravelPlanResponse.RequestSnapshot(
                        request.getDestination(),
                        request.getDepartureCity(),
                        request.getTravelDate().toString(),
                        request.getDays(),
                        request.getTravelerType(),
                        request.getBudget(),
                        request.getPace(),
                        readList(request.getInterestsJson()),
                        request.getNotes()
                ),
                plan.getMatchScore(),
                plan.getSummary(),
                planReasons(request),
                days,
                new TravelPlanResponse.BudgetBreakdown(
                        request.getBudget(),
                        stay,
                        food,
                        activity,
                        transportation,
                        reserve
                ),
                planRisks(),
                sources
        );
    }

    private TravelPlanResponse.PlanItem toPlanItem(TravelPlanItem item, TourismResource resource) {
        return new TravelPlanResponse.PlanItem(
                item.getResourceId(),
                item.getStartTime(),
                item.getLabel(),
                item.getTitle(),
                item.getDescription(),
                item.getTag(),
                tone(item.getTag()),
                item.getEstimatedCost(),
                item.getSourceName(),
                resource == null ? null : resource.getAddress(),
                resource == null ? null : resource.getLongitude(),
                resource == null ? null : resource.getLatitude()
        );
    }

    private TourismResourceResponse toResourceResponse(TourismResource resource) {
        return new TourismResourceResponse(
                resource.getId(),
                categoryLabel(resource.getCategory()),
                resource.getName(),
                resource.getDistrict(),
                resource.getAddress(),
                resource.getLongitude(),
                resource.getLatitude(),
                resource.getSummary(),
                resource.getSourceName(),
                resource.getSourceUrl(),
                resource.getPriceMin(),
                resource.getPriceMax(),
                readList(resource.getTagsJson())
        );
    }

    private List<TourismResource> publishedResources(String city) {
        return tourismResourceRepository.findAllByCityIgnoreCaseAndStatusOrderByIdAsc(
                city,
                TourismResource.PublicationStatus.PUBLISHED
        );
    }

    private TravelPlan requireOwnedPlan(Long userId, Long planId) {
        return travelPlanRepository.findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new BusinessException("旅行计划不存在或无权访问。"));
    }

    private TourismResource requireResource(
            Map<String, TourismResource> resourcesByName,
            String resourceName
    ) {
        TourismResource resource = resourcesByName.get(resourceName);
        if (resource == null) {
            throw new BusinessException("旅行资源缺失：" + resourceName);
        }
        return resource;
    }

    private String planTitle(TripRequest request) {
        List<String> interests = readList(request.getInterestsJson());
        String style = interests.contains("非遗体验")
                ? "非遗慢游"
                : interests.contains("自然风景") ? "湖畔漫游" : "城市轻游";
        return request.getDestination() + style + request.getDays() + "日计划";
    }

    private String planSummary(TripRequest request) {
        List<String> interests = readList(request.getInterestsJson());
        String focus = interests.stream().limit(2).collect(Collectors.joining("、"));
        return "围绕" + focus + "安排，不过度填满日程，为"
                + request.getTravelerType() + "保留停留和临时调整的时间。";
    }

    private List<String> planReasons(TripRequest request) {
        return List.of(
                "预算控制在￥" + request.getBudget().setScale(0, RoundingMode.HALF_UP)
                        + "以内，住宿、餐饮和体验项目均保留调整空间。",
                request.getPace().contains("轻松")
                        ? "每天设置少量主要停留点，减少跨区往返，适合慢节奏体验。"
                        : "在主要停留点之外保留自由时间，方便根据现场状态调整。",
                "行程节点均关联资源来源，后续可由运营端审核、替换或下架。"
        );
    }

    private List<String> planRisks() {
        return List.of(
                "手作体验建议提前预约，周末名额可能较紧张。",
                "户外步行安排应根据天气调整，雨天可替换为室内文化场馆。"
        );
    }

    private String dayTitle(int day) {
        return day == 1 ? "运河手作与湖滨夜色" : "西湖慢行与宋韵收尾";
    }

    private BigDecimal transportationBudget(BigDecimal totalBudget) {
        return totalBudget.multiply(BigDecimal.valueOf(0.09))
                .max(BigDecimal.valueOf(120))
                .setScale(0, RoundingMode.HALF_UP);
    }

    private BigDecimal sumCosts(List<TravelPlanItem> items, String... tags) {
        List<String> tagList = List.of(tags);
        return items.stream()
                .filter(item -> tagList.contains(item.getTag()))
                .map(TravelPlanItem::getEstimatedCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal money(int value) {
        return BigDecimal.valueOf(value);
    }

    private String categoryLabel(TourismResource.Category category) {
        return switch (category) {
            case CULTURE -> "文化体验";
            case EXPERIENCE -> "手作体验";
            case FOOD -> "餐饮";
            case LODGING -> "住宿";
            case SCENIC -> "景点";
            case LEISURE -> "休闲";
        };
    }

    private String statusLabel(TravelPlan.Status status) {
        return switch (status) {
            case DRAFT -> "待决定";
            case SAVED -> "已收藏";
            case ADOPTED -> "已采纳";
        };
    }

    private String tone(String tag) {
        return switch (tag) {
            case "非遗体验" -> "teal";
            case "本地美食" -> "ochre";
            case "自然风景" -> "sage";
            case "住宿" -> "ink";
            default -> "terracotta";
        };
    }

    private String appendNote(String current, String addition) {
        if (!StringUtils.hasText(current)) {
            return addition;
        }
        return current.trim() + " " + addition;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean contains(String value, String target) {
        return value != null && value.contains(target);
    }

    private String writeList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize trip preferences.", exception);
        }
    }

    private List<String> readList(String json) {
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read trip preferences.", exception);
        }
    }

    private record PlannedStop(
            int day,
            int sequence,
            String time,
            String label,
            String resourceName,
            String description,
            String tag,
            BigDecimal cost
    ) {
    }
}
