package com.vernflow.modules.tourism.service;

import com.vernflow.modules.tourism.dto.TravelConversationRequest;
import com.vernflow.modules.tourism.dto.TravelConversationResponse.FieldChange;
import com.vernflow.modules.tourism.dto.TravelDraft;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TravelIntentService {

    private static final String SUPPORTED_CITY = "杭州";
    private static final List<String> CITIES = List.of(
            "杭州", "上海", "苏州", "南京", "宁波", "绍兴", "嘉兴", "湖州", "无锡", "北京", "成都", "广州"
    );
    private static final List<String> INTEREST_ORDER = List.of(
            "非遗体验", "本地美食", "自然风景", "人文历史", "亲子友好", "城市漫步", "夜游体验"
    );
    private static final Pattern DATE_PATTERN = Pattern.compile("(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})[日号]");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("(20\\d{2})[-/.](\\d{1,2})[-/.](\\d{1,2})");
    private static final Pattern DAYS_PATTERN = Pattern.compile("([一二两12])\\s*(?:天|日)");
    private static final Pattern BUDGET_PREFIX_PATTERN = Pattern.compile("(?:预算|费用|花费|人均)\\D{0,6}(\\d{3,5})");
    private static final Pattern BUDGET_SUFFIX_PATTERN = Pattern.compile("[¥￥]?(\\d{3,5})\\s*(?:元|块)");

    public Analysis analyze(TravelConversationRequest request) {
        String prompt = request.prompt().replaceAll("\\s+", " ").trim();
        DraftState state = new DraftState(request.current());
        List<String> warnings = new ArrayList<>();

        extractCities(prompt, state, warnings);
        extractDate(prompt, state);
        extractDays(prompt, state, warnings);
        extractBudget(prompt, state);
        extractTravelers(prompt, state);
        extractPace(prompt, state);
        extractInterests(prompt, state);
        extractNotes(prompt, state);

        List<String> missingFields = state.missingFields();
        boolean supported = !StringUtils.hasText(state.destination) || SUPPORTED_CITY.equals(state.destination);
        if (!supported) {
            warnings.add("当前首版路线库仅支持杭州，其他目的地将在资源库扩充后开放。");
        }
        boolean ready = missingFields.isEmpty() && supported;
        return new Analysis(
                state.toDraft(),
                List.copyOf(state.changes.values()),
                missingFields,
                warnings.stream().distinct().toList(),
                ready
        );
    }

    public String fallbackReply(Analysis analysis) {
        List<String> parts = new ArrayList<>();
        if (!analysis.changes().isEmpty()) {
            String changed = analysis.changes().stream().map(FieldChange::label).distinct().reduce((a, b) -> a + "、" + b).orElse("");
            parts.add("我已经把“" + changed + "”同步到右侧出行条件。\n");
        } else {
            parts.add("我记下了这次补充，右侧条件仍可随时修改。\n");
        }
        if (!analysis.warnings().isEmpty()) {
            parts.add(String.join("", analysis.warnings()));
        }
        if (analysis.readyToGenerate()) {
            parts.add("条件已经基本完整。你可以直接生成方案，或者继续告诉我不想太累、想避开人群等细节。");
        } else if (!analysis.missingFields().isEmpty()) {
            parts.add("接下来还需要确认：" + String.join("、", analysis.missingFields()) + "。");
        }
        return String.join("", parts);
    }

    private void extractCities(String prompt, DraftState state, List<String> warnings) {
        String departure = null;
        for (String city : CITIES) {
            if (prompt.contains("从" + city) || prompt.contains(city + "出发")) {
                departure = city;
                state.setDeparture(city);
                break;
            }
        }

        String destinationSource = prompt;
        if (departure != null) {
            destinationSource = destinationSource.replace("从" + departure + "出发", "")
                    .replace("从" + departure, "")
                    .replace(departure + "出发", "");
        }
        for (String city : CITIES) {
            if (destinationSource.contains(city)) {
                state.setDestination(city);
                if (!SUPPORTED_CITY.equals(city)) {
                    warnings.add("已识别目的地为" + city + "，但当前首版路线库仅支持杭州。");
                }
                return;
            }
        }
    }

    private void extractDate(String prompt, DraftState state) {
        LocalDate today = LocalDate.now();
        if (prompt.contains("大后天")) {
            state.setDate(today.plusDays(3).toString());
            return;
        }
        if (prompt.contains("后天")) {
            state.setDate(today.plusDays(2).toString());
            return;
        }
        if (prompt.contains("明天")) {
            state.setDate(today.plusDays(1).toString());
            return;
        }
        if (prompt.contains("周末")) {
            LocalDate saturday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
            if (prompt.contains("下周末")) {
                saturday = saturday.plusWeeks(1);
            }
            state.setDate(saturday.toString());
            return;
        }

        Matcher isoMatcher = ISO_DATE_PATTERN.matcher(prompt);
        if (isoMatcher.find()) {
            LocalDate parsed = safeDate(
                    Integer.parseInt(isoMatcher.group(1)),
                    Integer.parseInt(isoMatcher.group(2)),
                    Integer.parseInt(isoMatcher.group(3))
            );
            if (parsed != null) state.setDate(parsed.toString());
            return;
        }

        Matcher dateMatcher = DATE_PATTERN.matcher(prompt);
        if (!dateMatcher.find()) return;
        int year = dateMatcher.group(1) == null ? today.getYear() : Integer.parseInt(dateMatcher.group(1));
        int month = Integer.parseInt(dateMatcher.group(2));
        int day = Integer.parseInt(dateMatcher.group(3));
        LocalDate parsed = safeDate(year, month, day);
        if (parsed != null && dateMatcher.group(1) == null && parsed.isBefore(today)) {
            parsed = safeDate(year + 1, month, day);
        }
        if (parsed != null) state.setDate(parsed.toString());
    }

    private void extractDays(String prompt, DraftState state, List<String> warnings) {
        Matcher matcher = DAYS_PATTERN.matcher(prompt);
        if (!matcher.find()) return;
        int days = switch (matcher.group(1)) {
            case "一", "1" -> 1;
            case "二", "两", "2" -> 2;
            default -> 0;
        };
        if (days > 0) state.setDays(days);

        Matcher unsupported = Pattern.compile("(\\d+)\\s*(?:天|日)").matcher(prompt);
        if (unsupported.find() && Integer.parseInt(unsupported.group(1)) > 2) {
            warnings.add("当前首版支持一至两日行程，多日路线将在后续版本开放。");
        }
    }

    private void extractBudget(String prompt, DraftState state) {
        Matcher prefix = BUDGET_PREFIX_PATTERN.matcher(prompt);
        Matcher suffix = BUDGET_SUFFIX_PATTERN.matcher(prompt);
        String amount = prefix.find() ? prefix.group(1) : suffix.find() ? suffix.group(1) : null;
        if (amount != null) state.setBudget(new BigDecimal(amount));
    }

    private void extractTravelers(String prompt, DraftState state) {
        if (containsAny(prompt, "长辈", "老人", "父母")) {
            state.setTravelers("携长辈出行");
        } else if (containsAny(prompt, "孩子", "儿童", "亲子", "宝宝")) {
            state.setTravelers("亲子出行");
        } else if (containsAny(prompt, "情侣", "对象", "爱人", "伴侣")) {
            state.setTravelers("情侣出行");
        } else if (containsAny(prompt, "朋友", "同学", "闺蜜")) {
            state.setTravelers("朋友结伴");
        } else if (containsAny(prompt, "一个人", "独自", "自己去", "单人")) {
            state.setTravelers("独自出行");
        }
    }

    private void extractPace(String prompt, DraftState state) {
        if (containsAny(prompt, "不赶", "不要太赶", "慢慢", "慢游", "轻松", "悠闲")) {
            state.setPace("轻松一些");
        } else if (containsAny(prompt, "丰富", "充实", "多安排", "多去几个")) {
            state.setPace("丰富一点");
        } else if (containsAny(prompt, "随意", "看心情", "自由一点")) {
            state.setPace("随意一些");
        }
    }

    private void extractInterests(String prompt, DraftState state) {
        Map<String, List<String>> keywords = new LinkedHashMap<>();
        keywords.put("非遗体验", List.of("非遗", "手作", "传统工艺"));
        keywords.put("本地美食", List.of("美食", "小吃", "杭帮菜", "地道风味", "好吃"));
        keywords.put("自然风景", List.of("自然", "山水", "西湖", "湿地", "风景"));
        keywords.put("人文历史", List.of("历史", "文化", "博物馆", "宋韵", "古迹"));
        keywords.put("亲子友好", List.of("亲子", "孩子", "儿童", "宝宝"));
        keywords.put("城市漫步", List.of("漫步", "散步", "街区", "慢游", "逛一逛"));
        keywords.put("夜游体验", List.of("夜游", "夜景", "晚上逛"));

        Set<String> next = new LinkedHashSet<>(state.interests);
        for (Map.Entry<String, List<String>> entry : keywords.entrySet()) {
            boolean mentioned = entry.getValue().stream().anyMatch(prompt::contains);
            if (!mentioned) continue;
            boolean negated = entry.getValue().stream().anyMatch(keyword -> containsAny(
                    prompt,
                    "不要" + keyword,
                    "不想" + keyword,
                    "不喜欢" + keyword,
                    "对" + keyword + "不感兴趣"
            ));
            if (negated) next.remove(entry.getKey());
            else next.add(entry.getKey());
        }
        state.setInterests(INTEREST_ORDER.stream().filter(next::contains).toList());
    }

    private void extractNotes(String prompt, DraftState state) {
        if (!containsAny(prompt, "过敏", "轮椅", "老人", "长辈", "孩子", "宝宝", "不想", "不要", "避免", "雨天", "下午", "早起", "住宿")) {
            return;
        }
        if (!StringUtils.hasText(state.notes)) {
            state.setNotes(prompt);
        } else if (!state.notes.contains(prompt)) {
            state.setNotes(state.notes + "；" + prompt);
        }
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) return true;
        }
        return false;
    }

    private LocalDate safeDate(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException exception) {
            return null;
        }
    }

    public record Analysis(
            TravelDraft draft,
            List<FieldChange> changes,
            List<String> missingFields,
            List<String> warnings,
            boolean readyToGenerate
    ) {
    }

    private static final class DraftState {
        private String destination;
        private String departure;
        private String date;
        private Integer days;
        private String travelers;
        private BigDecimal budget;
        private String pace;
        private List<String> interests;
        private String notes;
        private final Map<String, FieldChange> changes = new LinkedHashMap<>();

        private DraftState(TravelDraft current) {
            TravelDraft source = current == null
                    ? new TravelDraft(null, null, null, null, null, null, null, List.of(), null)
                    : current;
            destination = trimToNull(source.destination());
            departure = trimToNull(source.departure());
            date = trimToNull(source.date());
            days = source.days();
            travelers = trimToNull(source.travelers());
            budget = source.budget();
            pace = trimToNull(source.pace());
            interests = source.interests() == null
                    ? new ArrayList<>()
                    : new ArrayList<>(source.interests().stream().filter(StringUtils::hasText).distinct().toList());
            notes = trimToNull(source.notes());
        }

        private void setDestination(String value) {
            if (!Objects.equals(destination, value)) {
                destination = value;
                change("destination", "目的地", value);
            }
        }

        private void setDeparture(String value) {
            if (!Objects.equals(departure, value)) {
                departure = value;
                change("departure", "出发地", value);
            }
        }

        private void setDate(String value) {
            if (!Objects.equals(date, value)) {
                date = value;
                change("date", "出发日期", value);
            }
        }

        private void setDays(Integer value) {
            if (!Objects.equals(days, value)) {
                days = value;
                change("days", "出行天数", value + " 天");
            }
        }

        private void setTravelers(String value) {
            if (!Objects.equals(travelers, value)) {
                travelers = value;
                change("travelers", "同行伙伴", value);
            }
        }

        private void setBudget(BigDecimal value) {
            if (!Objects.equals(budget, value)) {
                budget = value;
                change("budget", "预算", "¥" + value.stripTrailingZeros().toPlainString());
            }
        }

        private void setPace(String value) {
            if (!Objects.equals(pace, value)) {
                pace = value;
                change("pace", "旅行节奏", value);
            }
        }

        private void setInterests(List<String> value) {
            if (!Objects.equals(interests, value)) {
                interests = new ArrayList<>(value);
                change("interests", "旅行偏好", String.join("、", value));
            }
        }

        private void setNotes(String value) {
            if (!Objects.equals(notes, value)) {
                notes = value;
                change("notes", "其他要求", value);
            }
        }

        private void change(String field, String label, String value) {
            changes.put(field, new FieldChange(field, label, value));
        }

        private List<String> missingFields() {
            List<String> missing = new ArrayList<>();
            if (!StringUtils.hasText(destination)) missing.add("目的地");
            if (!StringUtils.hasText(departure)) missing.add("出发地");
            if (!StringUtils.hasText(date)) missing.add("出发日期");
            if (days == null) missing.add("出行天数");
            if (!StringUtils.hasText(travelers)) missing.add("同行伙伴");
            if (budget == null || budget.compareTo(BigDecimal.valueOf(1200)) < 0) missing.add("预算");
            if (!StringUtils.hasText(pace)) missing.add("旅行节奏");
            if (interests.isEmpty()) missing.add("旅行偏好");
            return missing;
        }

        private TravelDraft toDraft() {
            return new TravelDraft(
                    destination,
                    departure,
                    date,
                    days,
                    travelers,
                    budget,
                    pace,
                    List.copyOf(interests),
                    notes
            );
        }

        private static String trimToNull(String value) {
            return StringUtils.hasText(value) ? value.trim() : null;
        }
    }
}
