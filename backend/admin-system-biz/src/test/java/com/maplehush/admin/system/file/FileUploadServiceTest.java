package com.maplehush.admin.system.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.common.storage.config.OssProperties;
import com.maplehush.admin.common.storage.service.IOssStorageService;
import com.maplehush.admin.config.redis.RedisKeyBuilder;
import com.maplehush.admin.system.file.service.IImageValidationService;
import com.maplehush.admin.system.file.service.impl.FileUploadServiceImpl;
import com.maplehush.admin.system.file.support.UploadRateLimiter;
import com.maplehush.admin.system.permission.service.IPermissionService;
import com.maplehush.admin.system.user.entity.SysUser;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.multipart.MultipartFile;

import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

/**
 * 仅隔离不可用 Redis，验证对象写入后的凭据保存失败会补偿同一个对象
 */
class FileUploadServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void receiptWriteFailureDeletesUploadedObjectAndReturnsDependencyError() {
        var images = mock(IImageValidationService.class);
        var storage = mock(IOssStorageService.class);
        var permissions = mock(IPermissionService.class);
        var user = new SysUser();
        user.setId(7L);
        when(permissions.current())
                .thenReturn(new IPermissionService.Context(user, List.of(), null));
        when(images.normalize(any())).thenReturn(new byte[] {1, 2, 3});
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        doThrow(new IllegalStateException("private-redis-error"))
                .when(values)
                .set(anyString(), anyString(), any(Duration.class));
        var service =
                new FileUploadServiceImpl(
                        images,
                        storage,
                        new OssProperties(),
                        permissions,
                        redis,
                        new RedisKeyBuilder("test"),
                        JsonMapper.builder().build(),
                        mock(UploadRateLimiter.class));
        var error =
                assertThrows(
                        BusinessException.class, () -> service.upload(mock(MultipartFile.class)));
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getErrorCode());
        var key = ArgumentCaptor.forClass(String.class);
        verify(storage).put(key.capture(), any(), any());
        verify(storage).delete(key.getValue());
    }
}
