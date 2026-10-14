package com.maplehush.admin.system.dict.vo;

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
        String id,
        String dictCode,
        String name,
        Integer status,
        String remark,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
