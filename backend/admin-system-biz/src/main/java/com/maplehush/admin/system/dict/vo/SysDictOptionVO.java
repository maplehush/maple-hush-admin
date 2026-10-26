package com.maplehush.admin.system.dict.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 普通已登录用户的有效字典选项，不暴露管理备注或实体
 * @param label 显示文本
 * @param value 存储值
 * @param color 可选小写六位 HEX，未配置为空
 * @param defaultItem 是否为默认项
 */
public record SysDictOptionVO(@Schema(description = "是否为默认项") String label, @Schema(description = "数据值") String value, @Schema(description = "颜色值；为空时由业务使用默认样式") String color, @Schema(description = "是否默认项") Boolean defaultItem) {}
