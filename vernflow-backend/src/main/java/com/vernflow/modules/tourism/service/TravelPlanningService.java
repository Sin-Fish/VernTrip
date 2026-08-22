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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final DynamicTourismCatalogService dynamicTourismCatalogService;

    public TravelPlanningService(
            UserAuthService userAuthService,
            TourismResourceRepository tourismResourceRepository,
            TripRequestRepository tripRequestRepository,
            TravelPlanRepository travelPlanRepository,
            TravelPlanItemRepository travelPlanItemRepository,
            TripFeedbackRepository tripFeedbackRepository,
            ObjectMapper objectMapper,
            OpenClawItineraryService openClawItineraryService,
            OpenClawProperties openClawProperties,
            DynamicTourismCatalogService dynamicTourismCatalogService
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
        this.dynamicTourismCatalogService = dynamicTourismCatalogService;
    }

    @Transactional
    public List<TourismResourceResponse> listResources(String city) {
        String targetCity = StringUtils.hasText(city) ? city.trim() : SUPPORTED_CITY;
        return dynamicTourismCatalogService.resourcesFor(targetCity).stream()
                .map(this::toResourceResponse)
                .toList();
    }

    @Transactional
    public TravelPlanResponse createPlan(Long userId, TravelPlanCreateRequest request) {
        userAuthService.requireUser(userId);
        BigDecimal minimumBudget = minimumBudgetForDays(request.days());
        if (request.budget().compareTo(minimumBudget) < 0) {
            throw new BusinessException("当前天数的总预算建议不低于￥" + minimumBudget.toPlainString() + "。");
        }
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
        if ("node".equals(type)) {
            return adjustPlanNode(userId, previousPlan, previousRequest, adjustment);
        }

        BigDecimal budget = previousRequest.getBudget();
        String pace = previousRequest.getPace();
        String notes = previousRequest.getNotes();

        switch (type) {
            case "budget" -> budget = budget.subtract(BigDecimal.valueOf(500))
                    .max(minimumBudgetForDays(previousRequest.getDays()));
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

    private TravelPlanResponse adjustPlanNode(
            Long userId,
            TravelPlan previousPlan,
            TripRequest request,
            TravelPlanAdjustRequest adjustment
    ) {
        if (adjustment.itemId() == null) {
            throw new BusinessException("请选择需要调整的行程节点。");
        }
        if (!StringUtils.hasText(adjustment.action())) {
            throw new BusinessException("请选择节点调整方式。");
        }

        String action = adjustment.action().trim().toLowerCase(Locale.ROOT);
        List<TravelPlanItem> previousItems = travelPlanItemRepository
                .findAllByPlanIdOrderByDayNumberAscSequenceNoAsc(previousPlan.getId());
        TravelPlanItem targetItem = previousItems.stream()
                .filter(item -> adjustment.itemId().equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("所选节点不属于当前行程。"));

        if ("delete".equals(action)) {
            if (request.getDays() > 1 && "住宿".equals(targetItem.getTag())) {
                throw new BusinessException("两日行程需要保留住宿节点，可以使用 AI 替换为其他住宿。");
            }
            long dayItemCount = previousItems.stream()
                    .filter(item -> item.getDayNumber() == targetItem.getDayNumber())
                    .count();
            if (dayItemCount <= 2) {
                throw new BusinessException("每天至少需要保留两个行程节点。");
            }
        } else if ("time".equals(action) && !StringUtils.hasText(adjustment.time())) {
            throw new BusinessException("请选择新的到达时间。");
        } else if (!List.of("replace", "delete", "time").contains(action)) {
            throw new BusinessException("不支持的节点调整方式：" + adjustment.action());
        }

        List<TourismResource> publishedResources = dynamicTourismCatalogService.resourcesFor(
                request.getDestination()
        );
        Map<Long, TourismResource> resourcesById = tourismResourceRepository.findAllById(
                        previousItems.stream().map(TravelPlanItem::getResourceId).filter(Objects::nonNull).toList()
                ).stream()
                .collect(Collectors.toMap(TourismResource::getId, Function.identity()));
        TourismResource targetResource = resourcesById.get(targetItem.getResourceId());
        if (targetResource == null) {
            throw new BusinessException("所选节点关联的资源已不存在，暂时无法调整。");
        }

        TourismResource replacementResource = null;
        OpenClawItineraryService.AgentReplacement agentReplacement = null;
        if ("replace".equals(action)) {
            Set<Long> lockedResourceIds = previousItems.stream()
                    .filter(item -> !item.getId().equals(targetItem.getId()))
                    .map(TravelPlanItem::getResourceId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(HashSet::new));
            List<TourismResource> candidates = publishedResources.stream()
                    .filter(resource -> resource.getCategory() == targetResource.getCategory())
                    .filter(resource -> !resource.getId().equals(targetResource.getId()))
                    .filter(resource -> !lockedResourceIds.contains(resource.getId()))
                    .toList();
            if (candidates.isEmpty()) {
                throw new BusinessException("当前没有同类型且未使用的候选资源，请先在商家端补充资源。");
            }

            List<String> lockedNames = previousItems.stream()
                    .filter(item -> !item.getId().equals(targetItem.getId()))
                    .map(TravelPlanItem::getTitle)
                    .toList();
            agentReplacement = openClawItineraryService.selectReplacement(
                    userId,
                    request,
                    targetResource,
                    candidates,
                    lockedNames,
                    adjustment.note()
            ).orElse(null);
            if (agentReplacement != null) {
                if (!agentReplacement.satisfiesRequest()) {
                    throw new BusinessException("当前资源库没有合适的替换项：" + agentReplacement.reason());
                }
                String selectedName = agentReplacement.resourceName();
                replacementResource = candidates.stream()
                        .filter(candidate -> candidate.getName().equals(selectedName))
                        .findFirst()
                        .orElseThrow();
            } else {
                replacementResource = selectLocalReplacement(candidates, request, adjustment.note());
            }
        }

        List<NodeItemDraft> drafts = new ArrayList<>();
        Map<Integer, Integer> daySequences = new LinkedHashMap<>();
        for (TravelPlanItem item : previousItems) {
            boolean target = item.getId().equals(targetItem.getId());
            if (target && "delete".equals(action)) {
                continue;
            }

            TourismResource resource = target && replacementResource != null
                    ? replacementResource
                    : resourcesById.get(item.getResourceId());
            if (resource == null) {
                throw new BusinessException("行程节点关联的资源已不存在：" + item.getTitle());
            }
            int sequence = daySequences.merge(item.getDayNumber(), 1, Integer::sum);
            String description = item.getDescription();
            String tag = item.getTag();
            BigDecimal cost = item.getEstimatedCost();
            if (target && replacementResource != null) {
                description = agentReplacement == null
                        ? localReplacementReason(replacementResource, adjustment.note())
                        : agentReplacement.reason();
                tag = resourceTag(replacementResource.getCategory());
                cost = replacementCost(replacementResource, item.getEstimatedCost());
            }
            drafts.add(new NodeItemDraft(
                    resource,
                    item.getDayNumber(),
                    sequence,
                    target && "time".equals(action) ? adjustment.time() : item.getStartTime(),
                    item.getLabel(),
                    description,
                    tag,
                    cost
            ));
        }

        BigDecimal estimatedBudget = drafts.stream()
                .map(NodeItemDraft::cost)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(transportationBudget(request.getBudget()));
        String generationEngine = switch (action) {
            case "replace" -> agentReplacement != null
                    ? "openclaw"
                    : openClawProperties.primaryEnabled() ? "local-fallback" : "local";
            default -> previousPlan.getGenerationEngine();
        };
        TravelPlan nextPlan = travelPlanRepository.save(TravelPlan.create(
                userId,
                request.getId(),
                previousPlan.getId(),
                previousPlan.getVersion() + 1,
                previousPlan.getTitle(),
                nodeAdjustmentSummary(action, targetItem, replacementResource),
                previousPlan.getMatchScore(),
                estimatedBudget,
                generationEngine
        ));
        travelPlanItemRepository.saveAll(drafts.stream()
                .map(draft -> TravelPlanItem.create(
                        nextPlan.getId(),
                        draft.resource(),
                        draft.day(),
                        draft.sequence(),
                        draft.time(),
                        draft.label(),
                        draft.description(),
                        draft.tag(),
                        draft.cost()
                ))
                .toList());
        return toResponse(nextPlan);
    }

    private TourismResource selectLocalReplacement(
            List<TourismResource> candidates,
            TripRequest request,
            String note
    ) {
        return candidates.stream()
                .max(Comparator.comparingInt(candidate -> replacementScore(candidate, request, note)))
                .orElseThrow(() -> new BusinessException("没有可用的替换资源。"));
    }

    private int replacementScore(TourismResource candidate, TripRequest request, String note) {
        String resourceText = String.join(" ",
                candidate.getName(),
                candidate.getDistrict(),
                candidate.getSummary(),
                candidate.getTagsJson(),
                candidate.getSuitablePeopleJson()
        );
        int score = 0;
        for (String interest : readList(request.getInterestsJson())) {
            if (resourceText.contains(interest)) {
                score += 3;
            }
        }
        if (StringUtils.hasText(note)) {
            for (String keyword : note.trim().split("[，。；、\\s]+")) {
                if (keyword.length() >= 2 && resourceText.contains(keyword)) {
                    score += 5;
                }
            }
        }
        if (resourceText.contains(request.getTravelerType().replace("出行", ""))) {
            score += 2;
        }
        return score;
    }

    private String localReplacementReason(TourismResource resource, String note) {
        String suffix = StringUtils.hasText(note) ? "，并优先响应“" + note.trim() + "”" : "";
        return "替换为" + resource.getName() + "，保留当前时段与其余节点不变" + suffix + "。";
    }

    private BigDecimal replacementCost(TourismResource resource, BigDecimal previousCost) {
        BigDecimal minimum = resource.getPriceMin() == null ? BigDecimal.ZERO : resource.getPriceMin();
        BigDecimal maximum = resource.getPriceMax() == null ? minimum : resource.getPriceMax();
        return previousCost.max(minimum).min(maximum).setScale(0, RoundingMode.HALF_UP);
    }

    private String nodeAdjustmentSummary(
            String action,
            TravelPlanItem target,
            TourismResource replacement
    ) {
        return switch (action) {
            case "replace" -> "已将“" + target.getTitle() + "”替换为“" + replacement.getName()
                    + "”，其余行程节点保持不变。";
            case "delete" -> "已移除“" + target.getTitle() + "”，并保持其余行程节点与顺序不变。";
            case "time" -> "已调整“" + target.getTitle() + "”的到达时间，其余行程节点保持不变。";
            default -> throw new BusinessException("不支持的节点调整方式：" + action);
        };
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
        List<TourismResource> resources = dynamicTourismCatalogService.resourcesFor(request.getDestination());
        if (resources.isEmpty()) {
            throw new BusinessException("当前路线资源库暂不支持该目的地。");
        }

        Map<String, TourismResource> resourcesByName = resources.stream()
                .collect(Collectors.toMap(TourismResource::getName, Function.identity()));
        OpenClawItineraryService.AgentItinerary agentItinerary = openClawItineraryService
                .generate(request.getUserId(), request, resources)
                .orElse(null);
        List<PlannedStop> stops = agentItinerary == null
                ? plannedStops(request, resources)
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

    private List<PlannedStop> plannedStops(
            TripRequest request,
            List<TourismResource> resources
    ) {
        if ("杭州".equals(dynamicTourismCatalogService.normalizeCity(request.getDestination()))) {
            return hangzhouPlannedStops(request);
        }
        return catalogPlannedStops(request, resources);
    }

    private List<PlannedStop> hangzhouPlannedStops(TripRequest request) {
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
                        ? "连续入住" + (request.getDays() - 1) + "晚，选择交通便利、价格适中的住宿，把更多预算留给体验项目。"
                        : "连续入住" + (request.getDays() - 1) + "晚，放下行李后不安排远距离移动，为后续行程保留体力。",
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
        if (request.getDays() == 2) {
            return allStops;
        }

        for (int day = 3; day <= request.getDays(); day++) {
            int route = (day - 3) % 3;
            if (route == 0) {
                allStops.add(new PlannedStop(day, 1, "09:30", "丝路晨光",
                        "中国丝绸博物馆", "从丝绸与服饰工艺认识杭州，室内参观也适合作为天气变化时的稳妥安排。",
                        "文化体验", BigDecimal.ZERO));
                allStops.add(new PlannedStop(day, 2, "12:15", "午间杭州味",
                        "湖滨时令小馆", "在湖滨范围内用餐，减少跨区折返，为下午留出完整时间。",
                        "本地美食", secondMeal));
                allStops.add(new PlannedStop(day, 3, "14:30", "山溪轻行",
                        "九溪烟树轻徒步", "根据天气和同行人体力选择轻量路段，途中保留休息与自由拍摄时间。",
                        "自然风景", BigDecimal.ZERO));
            } else if (route == 1) {
                allStops.add(new PlannedStop(day, 1, "09:30", "茶山上午",
                        "龙井茶园采制体验", "用半天了解龙井茶的采制过程，预约时可说明同行人的年龄与体力情况。",
                        "非遗体验", activity));
                allStops.add(new PlannedStop(day, 2, "12:30", "运河午餐",
                        "运河家常杭帮菜", "回到运河片区享用家常杭帮菜，口味和份量可按同行人灵活调整。",
                        "本地美食", firstMeal));
                allStops.add(new PlannedStop(day, 3, "15:00", "街巷留白",
                        "小河直街午后散步", "不设必须完成的打卡点，以街巷、社区店铺和运河风景收住当天节奏。",
                        "人文历史", BigDecimal.ZERO));
            } else {
                allStops.add(new PlannedStop(day, 1, "09:30", "工艺再发现",
                        "中国杭州工艺美术博物馆", "结合前几日的城市体验重新认识杭州工艺，按兴趣选择重点展区。",
                        "文化体验", BigDecimal.ZERO));
                allStops.add(new PlannedStop(day, 2, "12:00", "湖边午餐",
                        "湖滨时令小馆", "选择当季杭州风味，在步行范围内完成午餐与休息。",
                        "本地美食", secondMeal));
                allStops.add(new PlannedStop(day, 3, "15:00", "宋韵收尾",
                        "清河坊宋韵夜游", "把最后一段时间留给自由探访，可根据返程时间随时缩短。",
                        "人文历史", nightVisit));
            }
        }
        return allStops;
    }

    private List<PlannedStop> catalogPlannedStops(
            TripRequest request,
            List<TourismResource> resources
    ) {
        List<TourismResource> activities = resources.stream()
                .filter(resource -> resource.getCategory() != TourismResource.Category.FOOD)
                .filter(resource -> resource.getCategory() != TourismResource.Category.LODGING)
                .toList();
        List<TourismResource> food = resources.stream()
                .filter(resource -> resource.getCategory() == TourismResource.Category.FOOD)
                .toList();
        List<TourismResource> lodging = resources.stream()
                .filter(resource -> resource.getCategory() == TourismResource.Category.LODGING)
                .toList();
        if (activities.size() < 2 || food.isEmpty() || (request.getDays() > 1 && lodging.isEmpty())) {
            throw new BusinessException(
                    "当前目的地的真实景点、餐饮或住宿资源不足，请配置高德 Web 服务 Key 后重试。"
            );
        }

        List<PlannedStop> stops = new ArrayList<>();
        for (int day = 1; day <= request.getDays(); day++) {
            Set<Long> dayResourceIds = new HashSet<>();
            TourismResource morning = selectCatalogResource(activities, (day - 1) * 2, dayResourceIds);
            TourismResource lunch = selectCatalogResource(food, day - 1, dayResourceIds);
            TourismResource afternoon = selectCatalogResource(activities, (day - 1) * 2 + 1, dayResourceIds);

            stops.add(catalogStop(
                    request,
                    morning,
                    day,
                    1,
                    "09:30",
                    "认识这座城"
            ));
            stops.add(catalogStop(
                    request,
                    lunch,
                    day,
                    2,
                    "12:15",
                    "在地午餐"
            ));
            stops.add(catalogStop(
                    request,
                    afternoon,
                    day,
                    3,
                    "14:30",
                    "下午慢游"
            ));

            if (day == 1 && request.getDays() > 1) {
                TourismResource hotel = selectCatalogResource(lodging, 0, dayResourceIds);
                stops.add(catalogStop(
                        request,
                        hotel,
                        day,
                        4,
                        "18:30",
                        "安顿入住"
                ));
            }
        }
        return stops;
    }

    private TourismResource selectCatalogResource(
            List<TourismResource> resources,
            int preferredIndex,
            Set<Long> usedResourceIds
    ) {
        for (int offset = 0; offset < resources.size(); offset++) {
            TourismResource resource = resources.get(Math.floorMod(preferredIndex + offset, resources.size()));
            if (usedResourceIds.add(resource.getId())) {
                return resource;
            }
        }
        throw new BusinessException("当前目的地没有足够的不同类型资源用于编排行程。");
    }

    private PlannedStop catalogStop(
            TripRequest request,
            TourismResource resource,
            int day,
            int sequence,
            String time,
            String label
    ) {
        String description = resource.getSummary()
                + (StringUtils.hasText(resource.getOpenHours())
                        ? " 参考开放时间：" + resource.getOpenHours() + "。"
                        : "");
        return new PlannedStop(
                day,
                sequence,
                time,
                label,
                resource.getName(),
                description,
                resourceTag(resource.getCategory()),
                catalogCost(request, resource)
        );
    }

    private BigDecimal catalogCost(TripRequest request, TourismResource resource) {
        BigDecimal suggested = switch (resource.getCategory()) {
            case LODGING -> request.getBudget().multiply(BigDecimal.valueOf(0.24));
            case FOOD -> request.getBudget().multiply(BigDecimal.valueOf(0.05));
            default -> request.getBudget().multiply(BigDecimal.valueOf(0.025));
        };
        BigDecimal minimum = resource.getPriceMin() == null ? BigDecimal.ZERO : resource.getPriceMin();
        BigDecimal maximum = resource.getPriceMax() == null ? minimum : resource.getPriceMax();
        BigDecimal cost = suggested.max(minimum);
        if (maximum.signum() > 0) {
            cost = cost.min(maximum.max(minimum));
        }
        return cost.setScale(0, RoundingMode.HALF_UP);
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
                        dayTitle(entry.getKey(), request.getDestination()),
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
                item.getId(),
                item.getResourceId(),
                item.getStartTime(),
                item.getLabel(),
                item.getTitle(),
                item.getDescription(),
                item.getTag(),
                tone(item.getTag()),
                item.getEstimatedCost(),
                item.getSourceName(),
                resource == null ? null : resource.getPlace().getName(),
                resource == null ? null : resource.getPlace().getVerificationStatus().name(),
                resource == null ? null : resource.getAddress(),
                resource == null ? null : resource.getAmapPoiId(),
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
                resource.getPlace().getName(),
                resource.getPlace().getVerificationStatus().name(),
                resource.getAddress(),
                resource.getAmapPoiId(),
                resource.getLongitude(),
                resource.getLatitude(),
                resource.getSummary(),
                resource.getSourceName(),
                resource.getSourceUrl(),
                resource.getOpenHours(),
                resource.getPriceMin(),
                resource.getPriceMax(),
                resource.isReservationRequired(),
                resource.getAvailableCapacity(),
                availabilityStatus(resource),
                readList(resource.getTagsJson())
        );
    }

    private String availabilityStatus(TourismResource resource) {
        if (!resource.isReservationRequired()) {
            return "DIRECT";
        }
        if (resource.getAvailableCapacity() != null && resource.getAvailableCapacity() == 0) {
            return "SOLD_OUT";
        }
        if (resource.getDailyCapacity() != null
                && resource.getAvailableCapacity() != null
                && resource.getAvailableCapacity() * 4 <= resource.getDailyCapacity()) {
            return "LIMITED";
        }
        return "AVAILABLE";
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

    private String dayTitle(int day, String destination) {
        if (!"杭州".equals(dynamicTourismCatalogService.normalizeCity(destination))) {
            return destination + "探索第 " + day + " 日";
        }
        return switch (day) {
            case 1 -> "运河手作与湖滨夜色";
            case 2 -> "西湖慢行与宋韵街巷";
            case 3 -> "丝绸文脉与九溪山色";
            case 4 -> "龙井茶事与运河日常";
            case 5 -> "工艺回望与宋韵收尾";
            default -> "杭州慢游第" + day + "日";
        };
    }

    private BigDecimal minimumBudgetForDays(int days) {
        return BigDecimal.valueOf(Math.max(1, days) * 600L);
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

    private String resourceTag(TourismResource.Category category) {
        return switch (category) {
            case SCENIC -> "自然风景";
            case CULTURE -> "文化体验";
            case EXPERIENCE -> "非遗体验";
            case FOOD -> "本地美食";
            case LODGING -> "住宿";
            case LEISURE -> "人文历史";
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

    private record NodeItemDraft(
            TourismResource resource,
            int day,
            int sequence,
            String time,
            String label,
            String description,
            String tag,
            BigDecimal cost
    ) {
    }
}
