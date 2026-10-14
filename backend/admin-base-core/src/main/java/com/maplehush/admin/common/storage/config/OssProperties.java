package com.maplehush.admin.common.storage.config;

import jakarta.annotation.PostConstruct;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

/**
 * 后端受控 OSS 配置；不生成 toString，避免凭据被日志输出
 */
@Getter
@Setter
@ConfigurationProperties("maple-hush.oss")
public class OssProperties {
    /**
     * 地域，兼容用户提供的 oss-cn-beijing 与 SDK 的 cn-beijing
     */
    private String region = "oss-cn-beijing";

    /**
     * 独立公共读头像 Bucket
     */
    private String bucket = "maple-hush";

    /**
     * 无路径、参数或用户信息的 HTTPS 公开域名
     */
    private String publicDomain = "https://maple-hush.oss-cn-beijing.aliyuncs.com";

    /**
     * 对象基础目录，必须以 / 结尾
     */
    private String objectPrefix = "vben-admin-img/";

    /**
     * 仅由后端环境注入的 AccessKey ID，不参与日志和响应
     */
    private String accessKeyId = "";

    /**
     * 仅由后端环境注入的 AccessKey Secret
     */
    private String accessKeySecret = "";

    /**
     * 可选短期凭据安全令牌
     */
    private String securityToken = "";

    /**
     * 启动只校验静态地址，不联网或要求未使用头像功能的服务提供凭据
     */
    @PostConstruct
    public void validate() {
        URI domain = URI.create(publicDomain);
        if (!"https".equals(domain.getScheme())
                || domain.getHost() == null
                || domain.getRawUserInfo() != null
                || domain.getRawQuery() != null
                || domain.getRawFragment() != null
                || domain.getPort() != -1
                || (!domain.getRawPath().isEmpty() && !"/".equals(domain.getRawPath()))
                || !region.matches("(?:oss-)?[a-z0-9]+(?:-[a-z0-9]+)+")
                || !bucket.matches("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]")
                || !objectPrefix.matches("[a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)*/")) {
            throw new IllegalArgumentException("OSS 域名、地域、Bucket 或前缀配置无效");
        }
        publicDomain = publicDomain.replaceAll("/+$", "");
    }
}
