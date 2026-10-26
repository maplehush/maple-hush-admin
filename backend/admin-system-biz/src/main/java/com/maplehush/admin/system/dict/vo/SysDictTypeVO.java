package com.maplehush.admin.system.dict.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 字典类型管理输出
 * @param id 类型 ID 字符串
 * @param dictCode 稳定编码
 * @param name 显示名称，映射 dict_name
 * @param status 1 启用、0 停用
 * @param remark 管理备注
 * @param createTime 东八区秒级创建时间
 * @param updateTime 东八区秒级更新时间
 */
public record SysDictTypeVO(
        @Schema(description = "类型 ID 字符串", example = "1") String id,
        @Schema(description = "稳定编码") String dictCode,
        @Schema(description = "显示名称，映射 dict_name") String name,
        @Schema(description = "1 启用、0 停用", allowableValues = {"0", "1"}, example = "1") Integer status,
        @Schema(description = "管理备注") String remark,
        @Schema(description = "东八区秒级创建时间") LocalDateTime createTime,
        @Schema(description = "东八区秒级更新时间") LocalDateTime updateTime) {}
