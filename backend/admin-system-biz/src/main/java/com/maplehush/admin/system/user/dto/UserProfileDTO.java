package com.maplehush.admin.system.user.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 本人资料全量表单，姓名必填、简介 null/空白清除
 *
 * @param realName 姓名，映射 real_name
 * @param introduction 个人简介，null 或空白清除
 */
public record UserProfileDTO(
        @NotBlank @Size(max = 100) String realName, @Size(max = 500) String introduction) {
    /**
     * 拒绝未知字段，不能通过本人接口修改账号、角色、状态或头像 URL
     * @param field 未知字段名称
     * @param ignored 未知字段值，不进入异常或日志
     */
    @JsonAnySetter
    public void rejectUnknown(String field, Object ignored) {
        throw new IllegalArgumentException("本人表单包含不支持的字段");
    }
}
