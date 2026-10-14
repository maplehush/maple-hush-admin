package com.maplehush.admin.system.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.maplehush.admin.system.auth.support.PasswordPolicy;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {
    @Test
    void rejectsTemporaryAndShortPasswords() {
        PasswordPolicy policy = new PasswordPolicy();
        assertThrows(
                IllegalArgumentException.class, () -> policy.validateNew("12345678", "old-secret"));
        assertThrows(
                IllegalArgumentException.class, () -> policy.validateNew("short", "old-secret"));
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.validateNew("old-secret", "old-secret"));
    }

    @Test
    void acceptsLongPassphrase() {
        assertDoesNotThrow(
                () ->
                        new PasswordPolicy()
                                .validateNew("maple-hush team passphrase 2026", "12345678"));
    }
}
