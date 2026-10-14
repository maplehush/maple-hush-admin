package com.maplehush.admin.common.storage.service.impl;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.models.DeleteObjectRequest;
import com.aliyun.sdk.service.oss2.models.GetBucketAclRequest;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.transport.BinaryData;
import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.common.storage.config.OssProperties;
import com.maplehush.admin.common.storage.service.IOssStorageService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * OSS SDK V2 适配；异常消息与凭据不进入客户端响应或日志
 */
@Service
public class OssStorageServiceImpl implements IOssStorageService {
    private static final Logger LOG = LoggerFactory.getLogger(OssStorageServiceImpl.class);
    private final ObjectProvider<OSSClient> clients;
    private final OssProperties properties;
    private final AtomicLong compensationFailures = new AtomicLong();

    public OssStorageServiceImpl(ObjectProvider<OSSClient> clients, OssProperties properties) {
        this.clients = clients;
        this.properties = properties;
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        checkKey(key);
        boolean attempted = false;
        try {
            OSSClient client = clients.getObject();
            var policy =
                    client.getBucketAcl(
                                    GetBucketAclRequest.newBuilder()
                                            .bucket(properties.getBucket())
                                            .build())
                            .accessControlPolicy();
            if (policy == null
                    || policy.accessControlList() == null
                    || !"public-read".equals(policy.accessControlList().grant())) {
                throw unavailable();
            }
            attempted = true;
            client.putObject(
                    PutObjectRequest.newBuilder()
                            .bucket(properties.getBucket())
                            .key(key)
                            .contentType(contentType)
                            .cacheControl("public, max-age=31536000, immutable")
                            .forbidOverwrite(true)
                            .body(BinaryData.fromBytes(bytes))
                            .build());
        } catch (RuntimeException exception) {
            if (attempted) {
                try {
                    delete(key);
                } catch (RuntimeException ignored) {
                    compensationFailures.incrementAndGet();
                    LOG.error(
                            "OSS upload compensation failed key={} failureCount={}",
                            key,
                            compensationFailures.get());
                }
            }
            LOG.error("OSS upload unavailable errorType={}", exception.getClass().getSimpleName());
            throw unavailable();
        }
    }

    @Override
    public void delete(String key) {
        checkKey(key);
        try {
            clients.getObject()
                    .deleteObject(
                            DeleteObjectRequest.newBuilder()
                                    .bucket(properties.getBucket())
                                    .key(key)
                                    .build());
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    @Override
    public long compensationFailureCount() {
        return compensationFailures.get();
    }

    private void checkKey(String key) {
        if (key == null
                || !key.startsWith(properties.getObjectPrefix())
                || key.contains("..")
                || !key.matches("[a-zA-Z0-9_/-]+\\.[a-zA-Z0-9]+")) {
            throw new IllegalArgumentException("OSS 对象键无效");
        }
    }

    private BusinessException unavailable() {
        return new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "头像存储暂不可用，请稍后重试");
    }
}
