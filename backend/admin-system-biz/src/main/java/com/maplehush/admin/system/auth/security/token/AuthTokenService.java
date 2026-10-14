package com.maplehush.admin.system.auth.security.token;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** 仅签发两种用途互斥的 HMAC JWT；开发环境未配置密钥时使用进程内随机密钥。 */
@Component
public class AuthTokenService {
    private final byte[] secret;

    public AuthTokenService(String configuredSecret) {
        this(configuredSecret, true);
    }

    @Autowired
    public AuthTokenService(@Value("${maple-hush.auth.jwt-secret:}") String configuredSecret,
            Environment environment) {
        this(configuredSecret, environment.acceptsProfiles(Profiles.of("dev")));
    }

    private AuthTokenService(String configuredSecret, boolean development) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            if (!development) {
                throw new IllegalArgumentException("非开发环境必须配置 JWT 签名密钥");
            }
            secret = new byte[32];
            new SecureRandom().nextBytes(secret);
        } else {
            secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
            if (secret.length < 32) {
                throw new IllegalArgumentException("JWT 密钥至少需要 32 字节");
            }
        }
    }

    public String issueAccess(long userId, long authVersion, String familyId) {
        return issue(userId, authVersion, "access", familyId, 15 * 60);
    }

    public String issuePasswordChange(long userId, long authVersion) {
        return issue(userId, authVersion, "password_change", null, 5 * 60);
    }

    public Claims verifyAccess(String token) {
        return verify(token, "access");
    }

    public Claims verifyPasswordChange(String token) {
        return verify(token, "password_change");
    }

    private String issue(long userId, long authVersion, String purpose, String familyId, int seconds) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer("maple-hush-admin").subject(Long.toString(userId))
                    .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(seconds)))
                    .jwtID(UUID.randomUUID().toString()).claim("typ", purpose)
                    .claim("ver", authVersion).claim("fid", familyId).build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException("无法签发凭据", exception);
        }
    }

    private Claims verify(String token, String purpose) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())
                    || !jwt.verify(new MACVerifier(secret))) {
                throw new IllegalArgumentException("凭据无效");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!"maple-hush-admin".equals(claims.getIssuer())
                    || !purpose.equals(claims.getStringClaim("typ"))
                    || claims.getExpirationTime() == null
                    || !claims.getExpirationTime().after(new Date())) {
                throw new IllegalArgumentException("凭据无效");
            }
            return new Claims(Long.parseLong(claims.getSubject()), claims.getLongClaim("ver"),
                    claims.getStringClaim("fid"));
        } catch (ParseException exception) {
            throw new IllegalArgumentException("凭据无效", exception);
        } catch (Exception exception) {
            if (exception instanceof IllegalArgumentException argumentException) {
                throw argumentException;
            }
            throw new IllegalArgumentException("凭据无效", exception);
        }
    }

    public record Claims(long userId, long authVersion, String familyId) {}
}
