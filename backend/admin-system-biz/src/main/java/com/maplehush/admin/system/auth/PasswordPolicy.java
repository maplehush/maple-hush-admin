package com.maplehush.admin.system.auth;

import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 新密码的统一最低要求，供首次改密及后续本人改密复用。 */
@Component
public class PasswordPolicy {
    public static final String TEMPORARY_PASSWORD = "12345678";
    private static final Set<String> COMMON = Set.of("12345678", "123456789", "1234567890",
            "password", "password123", "admin123", "qwerty123", "11111111");

    public void validateNew(String candidate, String currentPlaintext) {
        if (candidate == null || candidate.length() < 8 || candidate.length() > 128
                || candidate.equals(currentPlaintext)
                || COMMON.contains(candidate.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("新密码长度应为 8～128 位，且不能使用旧密码或常见密码");
        }
    }
}
