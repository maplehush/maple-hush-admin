package com.maplehush.admin.system.role.dto;

import com.maplehush.admin.system.role.support.SysRoleGrantRules;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/** 角色分页查询；日期范围按东八区整日、秒级闭区间解释。 */
public record SysRoleQueryDTO(int page, int pageSize, String name, Integer status,
                              LocalDateTime startTime, LocalDateTime endTime) {
    public static SysRoleQueryDTO parse(String page, String size, String name, String status, String start, String end) {
        int number = positive(page, Integer.MAX_VALUE); int limit = positive(size, 100);
        if (name != null && name.length() > 100) throw SysRoleGrantRules.invalid("角色名称查询过长");
        Integer state = status == null ? null : switch (status) {
            case "0" -> 0; case "1" -> 1; default -> throw SysRoleGrantRules.invalid("角色状态无效");
        };
        LocalDate from = date(start); LocalDate to = date(end);
        if (from != null && to != null && from.isAfter(to)) throw SysRoleGrantRules.invalid("开始日期不能晚于结束日期");
        return new SysRoleQueryDTO(number, limit, name == null || name.isBlank() ? null : name.strip(), state,
                from == null ? null : from.atStartOfDay(), to == null ? null : to.atTime(23, 59, 59));
    }

    private static int positive(String value, int max) {
        try {
            if (value == null || !value.matches("[1-9][0-9]{0,9}")) throw new NumberFormatException();
            int number = Integer.parseInt(value);
            if (number > max) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException exception) { throw SysRoleGrantRules.invalid("分页参数无效，pageSize 须为 1 至 100"); }
    }

    private static LocalDate date(String value) {
        if (value == null) return null;
        try {
            if (!value.matches("[1-9][0-9]{3}-[0-9]{2}-[0-9]{2}")) throw SysRoleGrantRules.invalid("日期须为 YYYY-MM-DD");
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) { throw SysRoleGrantRules.invalid("日期无效"); }
    }
}
