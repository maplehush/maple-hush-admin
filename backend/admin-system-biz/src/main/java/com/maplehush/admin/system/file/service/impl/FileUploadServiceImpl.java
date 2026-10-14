package com.maplehush.admin.system.file.service.impl;

import com.maplehush.admin.common.annotation.OperationLog;
import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.common.storage.config.OssProperties;
import com.maplehush.admin.common.storage.service.IOssStorageService;
import com.maplehush.admin.config.redis.RedisKeyBuilder;
import com.maplehush.admin.system.file.service.IFileUploadService;
import com.maplehush.admin.system.file.service.IImageValidationService;
import com.maplehush.admin.system.file.vo.FileUploadVO;
import com.maplehush.admin.system.permission.service.IPermissionService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.UUID;

/**
 * 公共图片素材上传；Redis 仅保存短期绑定凭据，素材 URL 不依赖凭据继续存活
 */
@Service
public class FileUploadServiceImpl implements IFileUploadService {
    private static final Logger LOG = LoggerFactory.getLogger(FileUploadServiceImpl.class);

    /**
     * 上传凭据有效 2 小时，过期不删除已交付的公开素材
     */
    private static final Duration RECEIPT_TTL = Duration.ofHours(2);

    private final IImageValidationService images;
    private final IOssStorageService storage;
    private final OssProperties properties;
    private final IPermissionService permissions;
    private final RedisTemplate<String, String> redis;
    private final RedisKeyBuilder keys;
    private final JsonMapper json;

    public FileUploadServiceImpl(
            IImageValidationService images,
            IOssStorageService storage,
            OssProperties properties,
            IPermissionService permissions,
            @Qualifier("stringValueRedisTemplate") RedisTemplate<String, String> redis,
            RedisKeyBuilder keys,
            JsonMapper json) {
        this.images = images;
        this.storage = storage;
        this.properties = properties;
        this.permissions = permissions;
        this.redis = redis;
        this.keys = keys;
        this.json = json;
    }

    @Override
    @OperationLog(value = "file:upload", targetType = "public_image")
    public FileUploadVO upload(MultipartFile file) {
        long userId = permissions.current().user().getId();
        byte[] png = images.normalize(file);
        String key = properties.getObjectPrefix() + "files/" + userId + "/" + random() + ".png";
        String url = properties.getPublicDomain() + "/" + key;
        storage.put(key, png, "image/png");
        String uploadId = random();
        try {
            redis.opsForValue()
                    .set(
                            keys.key("file_upload", uploadId),
                            json.writeValueAsString(new Receipt(userId, url)),
                            RECEIPT_TTL);
        } catch (RuntimeException exception) {
            // 尚未成功交付上传标识，补偿本次新对象；不打印 Redis 数据或 SDK 异常内容。
            try {
                storage.delete(key);
            } catch (RuntimeException ignored) {
                LOG.error("File upload receipt compensation failed key={}", key);
            }
            throw unavailable();
        }
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            name = "image.png";
        }
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "");
        if (name.isBlank()) {
            name = "image.png";
        }
        return new FileUploadVO(
                uploadId,
                name.substring(0, Math.min(255, name.length())),
                url,
                "image/png",
                png.length);
    }

    @Override
    public String resolveImage(String uploadId, long uploaderId) {
        if (uploadId == null || !uploadId.matches("[a-f0-9]{32}")) {
            throw invalid();
        }
        Receipt receipt;
        try {
            String value = redis.opsForValue().get(keys.key("file_upload", uploadId));
            if (value == null) {
                throw invalid();
            }
            receipt = json.readValue(value, Receipt.class);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable();
        }
        if (receipt.userId() != uploaderId) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能使用他人上传的文件标识");
        }
        return receipt.url();
    }

    private String random() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private BusinessException invalid() {
        return new BusinessException(ErrorCode.INVALID_ARGUMENT, "上传标识无效或已过期，请重新上传");
    }

    private BusinessException unavailable() {
        return new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "文件上传凭据服务暂不可用");
    }

    /**
     * 后端签发的短期凭据，不接受客户端直接提供 URL
     * @param userId 上传人 ID
     * @param url 后端生成的公开图片 URL
     */
    private record Receipt(long userId, String url) {}
}
