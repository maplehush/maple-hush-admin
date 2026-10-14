package com.maplehush.admin.system.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 登录、退出与刷新安全事件；仅有创建时间，不继承双时间基类
 */
@Getter
@Setter
@TableName("sys_login_log")
public class SysLoginLog {
    /**
     * 日志主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 可空用户 ID；账号删除后保留快照
     */
    private Long userId;

    /**
     * 登录账号快照
     */
    private String username;

    /**
     * LOGIN、LOGOUT 或 REFRESH
     */
    private String eventType;

    /**
     * 事件成功与否
     */
    private Boolean success;

    /**
     * 固定原因码，不含凭据
     */
    private String reasonCode;

    /**
     * 连接来源地址，不信任转发头
     */
    private String clientIp;

    /**
     * 截断的客户端标识，不含请求体
     */
    private String userAgent;

    /**
     * 请求关联标识
     */
    private String traceId;

    /**
     * 东八区秒级事件时间
     */
    private LocalDateTime createTime;
}
