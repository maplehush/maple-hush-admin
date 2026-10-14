package com.maplehush.admin.system.user.service.impl;

import com.maplehush.admin.common.storage.config.OssProperties;
import com.maplehush.admin.common.storage.service.IOssStorageService;
import com.maplehush.admin.system.user.service.IAvatarStorageService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 头像随机键与归属边界；只清理当前用户受控目录内生成的 PNG
 */
@Service
public class AvatarStorageServiceImpl implements IAvatarStorageService {
    private static final Logger LOG = LoggerFactory.getLogger(AvatarStorageServiceImpl.class);
    private final IOssStorageService storage;
    private final OssProperties properties;
    private final AtomicLong cleanupFailures = new AtomicLong();

    public AvatarStorageServiceImpl(IOssStorageService storage, OssProperties properties) {
        this.storage = storage;
        this.properties = properties;
    }

    @Override
    public String upload(long userId, byte[] png) {
        String key =
                properties.getObjectPrefix()
                        + userId
                        + "/"
                        + UUID.randomUUID().toString().replace("-", "")
                        + ".png";
        storage.put(key, png, "image/png");
        return properties.getPublicDomain() + "/" + key;
    }

    @Override
    public void deleteOwned(long userId, String url) {
        String start =
                properties.getPublicDomain() + "/" + properties.getObjectPrefix() + userId + "/";
        if (url == null
                || !url.startsWith(start)
                || !url.substring(start.length()).matches("[a-f0-9]{32}\\.png")) {
            return;
        }
        String key = url.substring(properties.getPublicDomain().length() + 1);
        try {
            storage.delete(key);
        } catch (RuntimeException exception) {
            cleanupFailures.incrementAndGet();
            LOG.error(
                    "Avatar cleanup requires compensation key={} failureCount={}",
                    key,
                    cleanupFailures.get());
        }
    }

    /**
     * 获取进程内补偿清理失败数，结合安全对象键日志由运维重试
     * @return 累计失败次数
     */
    public long cleanupFailureCount() {
        return cleanupFailures.get();
    }
}
