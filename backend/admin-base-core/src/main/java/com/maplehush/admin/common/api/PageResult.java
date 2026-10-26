package com.maplehush.admin.common.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 分页数据；具体分页参数在业务接口校验
 *
 * @param <T> 业务数据类型
 * @param items 当前页的数据列表
 * @param total 符合条件的总记录数，不是当前页条数
 */
public record PageResult<T>(@Schema(description = "当前页数据") List<T> items, @Schema(description = "总记录数", example = "1") long total) {
    public PageResult {
        items = List.copyOf(items);
        if (total < 0) {
            throw new IllegalArgumentException("total must be non-negative");
        }
    }
}
