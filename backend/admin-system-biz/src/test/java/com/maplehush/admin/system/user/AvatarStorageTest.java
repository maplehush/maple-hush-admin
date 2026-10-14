package com.maplehush.admin.system.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.maplehush.admin.common.storage.config.OssProperties;
import com.maplehush.admin.common.storage.service.IOssStorageService;
import com.maplehush.admin.system.user.service.impl.AvatarStorageServiceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * 验证随机对象键、当前用户归属、受控域名及清理故障隔离
 */
class AvatarStorageTest {
    private final IOssStorageService storage = mock(IOssStorageService.class);
    private final OssProperties properties = new OssProperties();
    private final AvatarStorageServiceImpl avatars =
            new AvatarStorageServiceImpl(storage, properties);

    @Test
    void generatedKeyIsUnpredictableAndBoundToCurrentUser() {
        String first = avatars.upload(7, new byte[] {1});
        String second = avatars.upload(7, new byte[] {1});
        assertFalse(first.equals(second));
        assertTrue(
                first.matches(
                        "https://maple-hush\\.oss-cn-beijing\\.aliyuncs\\.com/vben-admin-img/7/[a-f0-9]{32}\\.png"));
        avatars.deleteOwned(7, first);
        verify(storage).delete(first.substring(properties.getPublicDomain().length() + 1));
    }

    @Test
    void foreignOrSignedOrEscapedUrlsAreNeverDeleted() {
        String own = properties.getPublicDomain() + "/vben-admin-img/7/" + "a".repeat(32) + ".png";
        for (String url :
                java.util.List.of(
                        own + "?signature=secret",
                        own + "#fragment",
                        own.replace("/7/", "/8/"),
                        own.replace("https://", "http://"),
                        own.replace("aliyuncs.com/", "aliyuncs.com.evil.invalid/"),
                        own.replace("/7/", "/7/../"),
                        own.replace("a".repeat(32), "%2e%2e"))) {
            avatars.deleteOwned(7, url);
        }
        avatars.deleteOwned(7, null);
        verify(storage, never()).delete(any());
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void cleanupFailureIsCountedAndCannotChangeTheCommittedAvatar(CapturedOutput output) {
        String key = "vben-admin-img/7/" + "b".repeat(32) + ".png";
        doThrow(new IllegalStateException("private-storage-exception")).when(storage).delete(key);
        avatars.deleteOwned(7, properties.getPublicDomain() + "/" + key);
        assertEquals(1, avatars.cleanupFailureCount());
        assertTrue(output.getAll().contains(key));
        assertFalse(output.getAll().contains("private-storage-exception"));
    }
}
