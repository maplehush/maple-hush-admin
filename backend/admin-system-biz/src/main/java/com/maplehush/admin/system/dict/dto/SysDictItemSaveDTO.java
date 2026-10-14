package com.maplehush.admin.system.dict.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 字典项表单；不允许通过更新移动所属类型
 *
 * @param dictTypeId 所属类型的正整数 ID 字符串
 * @param label 显示文本，至多 200 字符
 * @param value 同类型内唯一的存储值，至多 200 字符
 * @param color 可选六位 HEX；null 或空字符串清空，保存转小写
 * @param sort 排序值，越小越靠前；创建缺省 0，更新省略保留
 * @param defaultItem 默认标记；创建缺省 false，更新省略保留；与启停状态独立
 * @param status 1 启用、0 停用；创建缺省 1，更新省略保留
 * @param remark 管理备注，至多 500 字符；空值清除
 */
public record SysDictItemSaveDTO(
        @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String dictTypeId,
        @NotBlank @Size(max = 200) String label,
        @NotBlank @Size(max = 200) String value,
        @Pattern(regexp = "|#[0-9a-fA-F]{6}") String color,
        Integer sort,
        Boolean defaultItem,
        @Min(0) @Max(1) Integer status,
        @Size(max = 500) String remark) {
    /**
     * 拒绝未声明字段，不接受任意扩展配置
     * @param field 未知字段名
     * @param ignored 未知字段值，不记入日志
     */
    @JsonAnySetter
    public void rejectUnknown(String field, Object ignored) {
        throw new IllegalArgumentException("字典项表单含未知字段");
    }
}
