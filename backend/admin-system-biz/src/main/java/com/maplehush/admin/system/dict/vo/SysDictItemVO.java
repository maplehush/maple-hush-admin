package com.maplehush.admin.system.dict.vo;

import java.time.LocalDateTime;

/**
 * 字典项管理输出，包括停用项
 * @param id 字典项 ID 字符串
 * @param dictTypeId 所属类型 ID 字符串
 * @param label 显示文本
 * @param value 存储值
 * @param color 可选小写六位 HEX，未配置为空
 * @param sort 排序值
 * @param defaultItem 是否为默认项，与启停独立
 * @param status 1 启用、0 停用
 * @param remark 管理备注
 * @param createTime 东八区秒级创建时间
 * @param updateTime 东八区秒级更新时间
 */
public record SysDictItemVO(
        String id,
        String dictTypeId,
        String label,
        String value,
        String color,
        Integer sort,
        Boolean defaultItem,
        Integer status,
        String remark,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
