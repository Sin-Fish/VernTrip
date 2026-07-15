package com.vernflow.common.util;

import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 文本规范化工具。
 */
public final class TextNormalizer {

    private static final Pattern SPLIT_PATTERN = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsIdeographic}]+");

    private TextNormalizer() {
    }

    /**
     * 将标题转为便于匹配的标准格式。
     *
     * @param source 原始标题
     * @return 标准化标题
     */
    public static String normalizeTitle(String source) {
        if (!StringUtils.hasText(source)) {
            return "";
        }
        return SPLIT_PATTERN.matcher(source.toLowerCase().trim())
                .replaceAll(" ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * 解析关键字列表。
     *
     * @param source 逗号分隔文本
     * @return 关键字列表
     */
    public static List<String> splitKeywords(String source) {
        if (!StringUtils.hasText(source)) {
            return List.of();
        }
        return Arrays.stream(source.split("[,，;；]"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    /**
     * 将关键字列表转为稳定存储格式。
     *
     * @param keywords 关键字列表
     * @return 逗号分隔字符串
     */
    public static String joinKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return "";
        }
        return keywords.stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.joining(", "));
    }
}
