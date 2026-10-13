package com.maplehush.admin.common.api;

import java.util.List;

/** 分页数据；具体分页参数在业务接口校验。 */
public record PageResult<T>(List<T> items, long total) {
    public PageResult {
        items = List.copyOf(items);
        if (total < 0) {
            throw new IllegalArgumentException("total must be non-negative");
        }
    }
}
