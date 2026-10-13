package com.maplehush.admin.system.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AuthTokenServiceTest {
    private final AuthTokenService tokens = new AuthTokenService("0123456789abcdef0123456789abcdef");

    @Test
    void passwordChangeTokenCannotActAsAccessToken() {
        String token = tokens.issuePasswordChange(7L, 2L);
        assertEquals(7L, tokens.verifyPasswordChange(token).userId());
        assertThrows(IllegalArgumentException.class, () -> tokens.verifyAccess(token));
    }

    @Test
    void accessTokenCannotChangePassword() {
        String token = tokens.issueAccess(7L, 2L, "family-1");
        assertEquals("family-1", tokens.verifyAccess(token).familyId());
        assertThrows(IllegalArgumentException.class, () -> tokens.verifyPasswordChange(token));
    }

    @Test
    void productionRejectsEmptySigningSecret() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        assertThrows(IllegalArgumentException.class, () -> new AuthTokenService("", environment));
    }
}
