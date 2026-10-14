package com.maplehush.admin.common.storage.config;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.credentials.StaticCredentialsProvider;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.time.Duration;

/**
 * SDK 客户端延迟创建；未配置凭据时只使头像上传返回依赖不可用
 */
@Configuration
@EnableConfigurationProperties(OssProperties.class)
public class OssConfiguration {
    /**
     * 创建共享且受 Spring 生命周期管理的 HTTPS OSS 客户端
     * @param properties 受控后端配置，不输出到日志
     * @return SDK 客户端，容器关闭时释放连接
     */
    @Bean(destroyMethod = "close")
    @Lazy
    public OSSClient ossClient(OssProperties properties) {
        if (properties.getAccessKeyId().isBlank() || properties.getAccessKeySecret().isBlank()) {
            throw new IllegalStateException("OSS 后端凭据未配置");
        }
        var credentials =
                properties.getSecurityToken().isBlank()
                        ? new StaticCredentialsProvider(
                                properties.getAccessKeyId(), properties.getAccessKeySecret())
                        : new StaticCredentialsProvider(
                                properties.getAccessKeyId(),
                                properties.getAccessKeySecret(),
                                properties.getSecurityToken());
        return OSSClient.newBuilder()
                .region(properties.getRegion().replaceFirst("^oss-", ""))
                .credentialsProvider(credentials)
                .connectTimeout(Duration.ofSeconds(5))
                .readWriteTimeout(Duration.ofSeconds(15))
                .retryMaxAttempts(2)
                .build();
    }
}
