package com.maplehush.admin.config.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;

import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * 写入东八区秒级业务时间，保留调用方显式赋值
 */
@Component
public class TimestampMetaObjectHandler implements MetaObjectHandler {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now(ZONE).truncatedTo(ChronoUnit.SECONDS);
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(
                metaObject,
                "updateTime",
                LocalDateTime.class,
                LocalDateTime.now(ZONE).truncatedTo(ChronoUnit.SECONDS));
    }
}
