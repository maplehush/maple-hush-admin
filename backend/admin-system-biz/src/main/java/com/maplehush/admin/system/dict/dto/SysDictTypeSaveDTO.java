package com.maplehush.admin.system.dict.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 字典类型表单；编码创建后保持稳定
 *
 * @param dictCode 小写字母开头的字母/数字/下划线编码，至多 100 字符；创建必填，更新可省略
 * @param name 类型显示名称，至多 100 字符
 * @param status 1 启用、0 停用；创建缺省 1，更新省略保留
 * @param remark 管理备注，至多 500 字符；空值清除
 */
public record SysDictTypeSaveDTO(
        @Pattern(regexp = "[a-z][a-z0-9_]{0,99}") String dictCode,
        @NotBlank @Size(max = 100) String name,
        @Min(0) @Max(1) Integer status,
        @Size(max = 500) String remark) {
    /**
     * 拒绝表单契约以外的字段，避免静默接受未知配置
     * @param field 未知字段名
     * @param ignored 未知字段值，不记入日志
     */
    @JsonAnySetter
    public void rejectUnknown(String field, Object ignored) {
        throw new IllegalArgumentException("字典类型表单含未知字段");
    }
}
