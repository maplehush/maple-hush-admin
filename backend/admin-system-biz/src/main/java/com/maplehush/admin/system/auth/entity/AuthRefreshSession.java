package com.maplehush.admin.system.auth.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 只保存 refresh 哈希及轮换状态，禁止作为 HTTP 出参。 */
@Getter
@Setter
public class AuthRefreshSession {
    private Long id;
    private Long userId;
    private Long authVersion;
    private String familyId;
    private String tokenHash;
    private LocalDateTime expireTime;
    private LocalDateTime usedTime;
    private LocalDateTime revokedTime;
}
