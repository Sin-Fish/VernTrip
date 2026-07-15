package com.vernflow.common.response;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 统一分页响应结构。
 *
 * @param items 当前页数据
 * @param totalItems 总记录数
 * @param totalPages 总页数
 * @param pageNumber 当前页号
 * @param pageSize 每页大小
 * @param <T> 数据类型
 */
public record PageResponse<T>(
        List<T> items,
        long totalItems,
        int totalPages,
        int pageNumber,
        int pageSize
) {

    /**
     * 从 Spring Page 转换为统一分页响应。
     *
     * @param page Spring 分页对象
     * @param <T> 数据类型
     * @return 统一分页响应
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
        );
    }
}
