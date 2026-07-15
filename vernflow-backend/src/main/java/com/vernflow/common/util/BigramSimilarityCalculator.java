package com.vernflow.common.util;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 使用二元组计算标题相似度。
 */
@Component
public class BigramSimilarityCalculator {

    /**
     * 计算两个标题的相似度，范围为 0 到 1。
     *
     * @param left 左侧文本
     * @param right 右侧文本
     * @return 相似度分值
     */
    public double calculate(String left, String right) {
        String normalized_left = TextNormalizer.normalizeTitle(left);
        String normalized_right = TextNormalizer.normalizeTitle(right);
        if (normalized_left.isEmpty() || normalized_right.isEmpty()) {
            return 0.0;
        }
        if (normalized_left.equals(normalized_right)) {
            return 1.0;
        }
        if (normalized_left.contains(normalized_right) || normalized_right.contains(normalized_left)) {
            int minimum_length = Math.min(normalized_left.length(), normalized_right.length());
            if (minimum_length >= 24) {
                return 0.93;
            }
        }

        Map<String, Integer> left_bigrams = collectBigrams(normalized_left);
        Map<String, Integer> right_bigrams = collectBigrams(normalized_right);
        int overlap_count = 0;
        int total_count = 0;
        for (Map.Entry<String, Integer> entry : left_bigrams.entrySet()) {
            total_count += entry.getValue();
            overlap_count += Math.min(entry.getValue(), right_bigrams.getOrDefault(entry.getKey(), 0));
        }
        for (Integer count : right_bigrams.values()) {
            total_count += count;
        }
        return total_count == 0 ? 0.0 : (2.0 * overlap_count) / total_count;
    }

    /**
     * 构造文本二元组频次表。
     *
     * @param source 输入文本
     * @return 二元组频次
     */
    private Map<String, Integer> collectBigrams(String source) {
        String compact_source = source.replace(" ", "");
        Map<String, Integer> bigrams = new HashMap<>();
        if (compact_source.length() == 1) {
            bigrams.put(compact_source, 1);
            return bigrams;
        }
        for (int index = 0; index < compact_source.length() - 1; index++) {
            String bigram = compact_source.substring(index, index + 2);
            bigrams.merge(bigram, 1, Integer::sum);
        }
        return bigrams;
    }
}
