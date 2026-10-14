package com.maplehush.admin.system.dict.vo;

/**
 * 普通已登录用户的有效字典选项，不暴露管理备注或实体
 * @param label 显示文本
 * @param value 存储值
 * @param color 可选小写六位 HEX，未配置为空
 * @param defaultItem 是否为默认项
 */
public record SysDictOptionVO(String label, String value, String color, Boolean defaultItem) {}
