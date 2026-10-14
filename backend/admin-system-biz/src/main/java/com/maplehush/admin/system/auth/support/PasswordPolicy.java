package com.maplehush.admin.system.auth.support;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * 新密码的统一最低要求，供首次改密及后续本人改密复用
 */
@Component
public class PasswordPolicy {
    public static final String TEMPORARY_PASSWORD = "12345678";
    private static final Set<String> COMMON =
            Set.of(
                    "12345678",
                    "123456789",
                    "1234567890",
                    "password",
                    "password123",
                    "admin123",
                    "qwerty123",
                    "11111111");

    public void validateNew(String candidate, String currentPlaintext) {
        if (candidate == null
                || candidate.length() < 8
                || candidate.length() > 128
                || candidate.getBytes(StandardCharsets.UTF_8).length > 72
                || candidate.equals(currentPlaintext)
                || COMMON.contains(candidate.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("新密码须至少 8 字符且 UTF-8 不超过 72 字节，不能使用旧密码或常见密码");
        }
    }
}
