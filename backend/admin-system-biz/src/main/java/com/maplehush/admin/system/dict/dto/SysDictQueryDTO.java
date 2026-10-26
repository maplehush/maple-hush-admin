package com.maplehush.admin.system.dict.dto;

import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;

/**
 * 字典分页与排序条件；列名由服务端白名单映射，不接收 SQL
 *
 * @param page 从 1 开始的页码
 * @param pageSize 每页 1 至 100 条
 * @param dictCode 类型编码包含筛选
 * @param name 类型名称包含筛选
 * @param dictTypeId 字典项所属类型；类型查询为空
 * @param label 字典项文本包含筛选
 * @param status 0/1 状态筛选，省略表示全部
 * @param sortBy 已通过白名单的排序键
 * @param ascending 是否升序，默认 true
 */
public record SysDictQueryDTO(
        @Schema(description = "页码字符串") int page,
        @Schema(description = "每页 1 至 100 条") int pageSize,
        @Schema(description = "类型编码包含筛选") String dictCode,
        @Schema(description = "名称筛选") String name,
        @Schema(description = "字典项所属类型；类型查询为空", example = "1") Long dictTypeId,
        @Schema(description = "文本筛选") String label,
        @Schema(description = "状态筛选", allowableValues = {"0", "1"}, example = "1") Integer status,
        @Schema(description = "sort/id/label/value/createTime，缺省 sort") String sortBy,
        @Schema(description = "是否升序，默认 true") boolean ascending) {
    /**
     * 构造类型分页条件
     * @param page 页码字符串
     * @param size 每页条数
     * @param code 编码筛选
     * @param name 名称筛选
     * @param status 状态筛选
     * @param sortBy id/dictCode/name/createTime，缺省 id
     * @param order asc/desc，缺省 asc
     * @return 已验证的类型查询
     */
    public static SysDictQueryDTO types(
            String page,
            String size,
            String code,
            String name,
            String status,
            String sortBy,
            String order) {
        String sort = sortBy == null ? "id" : sortBy;
        if (!Set.of("id", "dictCode", "name", "createTime").contains(sort)) {
            throw invalid();
        }
        return new SysDictQueryDTO(
                number(page, Integer.MAX_VALUE),
                number(size, 100),
                text(code, 100),
                text(name, 100),
                null,
                null,
                state(status),
                sort,
                ascending(order));
    }

    /**
     * 构造字典项分页条件
     * @param page 页码字符串
     * @param size 每页条数
     * @param type 所属类型的正整数 ID
     * @param label 文本筛选
     * @param status 状态筛选
     * @param sortBy sort/id/label/value/createTime，缺省 sort
     * @param order asc/desc，缺省 asc
     * @return 已验证的字典项查询
     */
    public static SysDictQueryDTO items(
            String page,
            String size,
            String type,
            String label,
            String status,
            String sortBy,
            String order) {
        String sort = sortBy == null ? "sort" : sortBy;
        if (!Set.of("sort", "id", "label", "value", "createTime").contains(sort)) {
            throw invalid();
        }
        Long id;
        try {
            if (type == null || !type.matches("[1-9][0-9]{0,18}")) {
                throw new IllegalArgumentException();
            }
            id = Long.valueOf(type);
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
        return new SysDictQueryDTO(
                number(page, Integer.MAX_VALUE),
                number(size, 100),
                null,
                null,
                id,
                text(label, 200),
                state(status),
                sort,
                ascending(order));
    }

    private static int number(String value, int max) {
        try {
            if (value == null || !value.matches("[1-9][0-9]{0,9}")) {
                throw new IllegalArgumentException();
            }
            int parsed = Integer.parseInt(value);
            if (parsed > max) {
                throw new IllegalArgumentException();
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private static String text(String value, int max) {
        if (value != null && value.length() > max) {
            throw invalid();
        }
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static Integer state(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "0" -> 0;
            case "1" -> 1;
            default -> throw invalid();
        };
    }

    private static boolean ascending(String value) {
        if (value == null || "asc".equals(value)) {
            return true;
        }
        if ("desc".equals(value)) {
            return false;
        }
        throw invalid();
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.INVALID_ARGUMENT, "字典分页、筛选或排序条件无效");
    }
}
