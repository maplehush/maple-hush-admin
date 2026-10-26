package com.maplehush.admin.system.dept.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 部门全量表单；空父 ID 或字符串 0 表示根部门
 *
 * @param pid 父节点 ID；根节点使用空值或协议约定的根值
 * @param name 业务显示名称
 * @param sort 排序值；越小越靠前，同值按 ID 升序
 * @param status 启用状态：1 启用，0 停用；表单为空时按创建或修改规则处理
 * @param remark 业务备注
 */
public record SysDeptSaveDTO(
        @Size(max = 19) @Schema(description = "父节点 ID；根节点使用空值或协议约定的根值") String pid,
        @NotBlank @Size(max = 100) @Schema(description = "业务显示名称") String name,
        @Schema(description = "排序值；越小越靠前，同值按 ID 升序") Integer sort,
        @Min(0) @Max(1) @Schema(description = "启用状态：1 启用，0 停用；表单为空时按创建或修改规则处理", allowableValues = {"0", "1"}, example = "1") Integer status,
        @Size(max = 500) @Schema(description = "业务备注") String remark) {}
