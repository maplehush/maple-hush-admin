package com.maplehush.admin.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标记关键写操作；仅保存固定操作码与数值目标 ID，禁止读取请求体作摘要。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {
    String value();
    String targetType() default "";
    /** 目标 ID 的方法参数序号，只接受正整数；-1 表示不记录。 */
    int targetIdArgument() default -1;
}
