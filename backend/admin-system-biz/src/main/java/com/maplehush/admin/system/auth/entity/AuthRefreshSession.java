package com.maplehush.admin.system.auth.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 只保存 refresh 哈希及轮换状态，禁止作为 HTTP 出参
 */
@Getter
@Setter
public class AuthRefreshSession {
    /**
     * 数据库主键 ID
     */
    private Long id;

    /**
     * 会话所属用户 ID
     */
    private Long userId;

    /**
     * 签发 refresh 时绑定的用户授权版本；与当前用户版本不符则拒绝刷新
     */
    private Long authVersion;

    /**
     * 会话族标识；同一登录的 refresh 轮换记录共用此值
     */
    private String familyId;

    /**
     * refresh 凭据的 SHA-256 哈希；不保存原始令牌
     */
    private String tokenHash;

    /**
     * 会话族绝对到期时间，使用东八区本地时间
     */
    private LocalDateTime expireTime;

    /**
     * refresh 凭据已被轮换使用的时间；未使用时为空
     */
    private LocalDateTime usedTime;

    /**
     * 会话被撤销的时间；未撤销时为空
     */
    private LocalDateTime revokedTime;
}
