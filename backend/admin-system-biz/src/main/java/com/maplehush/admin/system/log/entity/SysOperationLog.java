package com.maplehush.admin.system.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 关键管理操作审计快照；仅有创建时间，不继承双时间基类
 */
@Getter
@Setter
@TableName("sys_operation_log")
public class SysOperationLog {
    /**
     * 日志主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 可空操作人 ID；删除账号后保留快照
     */
    private Long actorUserId;

    /**
     * 操作人账号快照
     */
    private String actorUsername;

    /**
     * 服务端注解定义的操作码
     */
    private String operationCode;

    /**
     * 受控目标类型
     */
    private String targetType;

    /**
     * 显式指定的目标 ID
     */
    private String targetId;

    /**
     * 业务事务是否成功提交
     */
    private Boolean success;

    /**
     * 固定结果摘要，不保存入参或异常
     */
    private String summary;

    /**
     * HTTP 请求方法
     */
    private String requestMethod;

    /**
     * 实际请求路径，含路径实参，不含查询参数；历史记录可能为路由模板
     */
    private String requestPath;

    /**
     * 连接来源地址
     */
    private String clientIp;

    /**
     * 请求关联标识
     */
    private String traceId;

    /**
     * 东八区秒级创建时间
     */
    private LocalDateTime createTime;
}
