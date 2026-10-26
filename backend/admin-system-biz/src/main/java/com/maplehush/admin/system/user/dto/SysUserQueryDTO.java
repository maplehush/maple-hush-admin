package com.maplehush.admin.system.user.dto;

import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户分页条件；只允许已知字段，不接受客户端排序 SQL
 *
 * @param page 从 1 开始的页码
 * @param pageSize 每页 1 至 100 条
 * @param name 用户姓名的包含查询，不搜索账号或备注
 * @param deptId 所属部门 ID；未提供时不筛选
 * @param status 状态条件；未提供时不筛选
 * @param startTime 创建时间下界，包含所选日的 00:00:00
 * @param endTime 创建时间上界，包含所选日的 23:59:59
 */
public record SysUserQueryDTO(
        @Schema(description = "页码字符串") int page,
        @Schema(description = "每页 1 至 100 条") int pageSize,
        @Schema(description = "姓名筛选") String name,
        @Schema(description = "所属部门 ID；未提供时不筛选", example = "1") Long deptId,
        @Schema(description = "0 或 1；空值表示全部状态", allowableValues = {"0", "1"}, example = "1") Integer status,
        @Schema(description = "创建时间下界，包含所选日的 00:00:00") LocalDateTime startTime,
        @Schema(description = "创建时间上界，包含所选日的 23:59:59") LocalDateTime endTime) {
    /**
     * 解析 HTTP 查询字符串并校验数值与日期边界
     *
     * @param page 页码字符串
     * @param size 每页条数字符串
     * @param name 姓名筛选
     * @param dept 部门 ID 筛选
     * @param status 0 或 1；空值表示全部状态
     * @param start YYYY-MM-DD 开始日期
     * @param end YYYY-MM-DD 结束日期
     * @return 已验证的查询条件
     */
    public static SysUserQueryDTO parse(
            String page,
            String size,
            String name,
            String dept,
            String status,
            String start,
            String end) {
        try {
            if (!page.matches("[1-9][0-9]{0,9}") || !size.matches("[1-9][0-9]{0,2}")) {
                throw new IllegalArgumentException();
            }
            int number = Integer.parseInt(page);
            int limit = Integer.parseInt(size);
            if (limit > 100 || name != null && name.length() > 100) {
                throw new IllegalArgumentException();
            }
            Long department = null;
            if (dept != null) {
                if (!dept.matches("[1-9][0-9]{0,18}")) {
                    throw new IllegalArgumentException();
                }
                department = Long.valueOf(dept);
            }
            Integer state =
                    status == null
                            ? null
                            : switch (status) {
                                case "0" -> 0;
                                case "1" -> 1;
                                default -> throw new IllegalArgumentException();
                            };
            LocalDate from = date(start);
            LocalDate to = date(end);
            if (from != null && to != null && from.isAfter(to)) {
                throw new IllegalArgumentException();
            }
            return new SysUserQueryDTO(
                    number,
                    limit,
                    name == null || name.isBlank() ? null : name.strip(),
                    department,
                    state,
                    from == null ? null : from.atStartOfDay(),
                    to == null ? null : to.atTime(23, 59, 59));
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "分页、部门、状态或日期查询条件无效");
        }
    }

    private static LocalDate date(String value) {
        if (value == null) {
            return null;
        }
        if (!value.matches("[1-9][0-9]{3}-[0-9]{2}-[0-9]{2}")) {
            throw new IllegalArgumentException();
        }
        return LocalDate.parse(value);
    }
}
