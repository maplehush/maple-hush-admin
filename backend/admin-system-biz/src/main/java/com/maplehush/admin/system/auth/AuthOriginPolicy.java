package com.maplehush.admin.system.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** 生产来源只匹配显式配置的公开 Origin，不信任转发头或客户端 Host。 */
@Component
public class AuthOriginPolicy {
    private final boolean development;
    private final Set<String> allowed;

    public AuthOriginPolicy(@Value("${maple-hush.auth.allowed-origins:}") String origins, Environment environment) {
        development = environment.acceptsProfiles(Profiles.of("dev"));
        allowed = Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> normalize(s, false)).collect(Collectors.toUnmodifiableSet());
        if (!development && allowed.isEmpty()) throw new IllegalArgumentException("非开发环境必须配置认证请求公开 Origin");
    }

    public boolean isAllowed(HttpServletRequest request) {
        try {
            String origin = request.getHeader("Origin");
            String referer = request.getHeader("Referer");
            String source;
            if (origin != null) {
                if (java.util.Collections.list(request.getHeaders("Origin")).size() != 1) return false;
                source = normalize(origin, false);
            } else if (referer != null) {
                if (java.util.Collections.list(request.getHeaders("Referer")).size() != 1) return false;
                source = normalize(referer, true);
            } else return false;
            if (allowed.contains(source)) return true;
            return development && source.equals(normalize(request.getScheme() + "://"
                    + request.getServerName() + ":" + request.getServerPort(), false));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String normalize(String value, boolean referer) {
        URI uri = URI.create(value);
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                || (!referer && (uri.getRawQuery() != null || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())))
                || uri.getPort() == 0 || uri.getPort() > 65535) throw new IllegalArgumentException("Invalid Origin");
        scheme = scheme.toLowerCase(Locale.ROOT);
        int port = uri.getPort() < 0 ? (scheme.equals("https") ? 443 : 80) : uri.getPort();
        return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + ":" + port;
    }
}
