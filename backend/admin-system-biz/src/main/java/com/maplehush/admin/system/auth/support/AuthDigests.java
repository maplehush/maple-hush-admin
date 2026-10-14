package com.maplehush.admin.system.auth.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** 登录标识与 refresh 凭据的单向摘要。 */
public final class AuthDigests {
    private AuthDigests() {}

    public static String digest(String input) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
