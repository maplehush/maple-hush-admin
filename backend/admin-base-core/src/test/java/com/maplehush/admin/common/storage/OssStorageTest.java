package com.maplehush.admin.common.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.models.AccessControlList;
import com.aliyun.sdk.service.oss2.models.AccessControlPolicy;
import com.aliyun.sdk.service.oss2.models.GetBucketAclResult;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.storage.config.OssProperties;
import com.maplehush.admin.common.storage.service.impl.OssStorageServiceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * 以 SDK 客户端替身核对 ACL、上传参数及失败补偿，不访问阿里云
 */
class OssStorageTest {
    private static final String KEY = "vben-admin-img/7/0123456789abcdef0123456789abcdef.png";
    private final OSSClient client = mock(OSSClient.class);
    private final OssProperties properties = new OssProperties();

    @SuppressWarnings("unchecked")
    private OssStorageServiceImpl storage(String grant) {
        ObjectProvider<OSSClient> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(client);
        var response = mock(GetBucketAclResult.class);
        when(response.accessControlPolicy())
                .thenReturn(
                        AccessControlPolicy.newBuilder()
                                .accessControlList(
                                        AccessControlList.newBuilder().grant(grant).build())
                                .build());
        when(client.getBucketAcl(any())).thenReturn(response);
        return new OssStorageServiceImpl(provider, properties);
    }

    @Test
    void uploadUsesControlledBucketPngMetadataAndNoObjectAclChange() {
        storage("public-read").put(KEY, new byte[] {1, 2, 3}, "image/png");
        var argument = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(argument.capture());
        assertEquals("maple-hush", argument.getValue().bucket());
        assertEquals(KEY, argument.getValue().key());
        assertEquals("image/png", argument.getValue().contentType());
        assertTrue(argument.getValue().forbidOverwrite());
        assertEquals(null, argument.getValue().objectAcl());
    }

    @Test
    void unsafeOrPrivateBucketIsRejectedBeforeAnyWrite() {
        for (String grant : java.util.List.of("private", "public-read-write")) {
            assertThrows(
                    BusinessException.class,
                    () -> storage(grant).put(KEY, new byte[] {1}, "image/png"));
        }
        verify(client, never()).putObject(any());
        verify(client, never()).deleteObject(any());
    }

    @Test
    void uploadTimeoutCompensatesAnObjectWhoseWriteMayHaveCompleted() {
        var storage = storage("public-read");
        when(client.putObject(any())).thenThrow(new IllegalStateException("private-sdk-secret"));
        assertThrows(BusinessException.class, () -> storage.put(KEY, new byte[] {1}, "image/png"));
        verify(client).deleteObject(any());
        assertEquals(0, storage.compensationFailureCount());
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void failedCompensationIsObservableWithoutLoggingSdkSecrets(CapturedOutput output) {
        var storage = storage("public-read");
        when(client.putObject(any())).thenThrow(new IllegalStateException("private-sdk-secret"));
        when(client.deleteObject(any()))
                .thenThrow(new IllegalStateException("private-delete-secret"));
        assertThrows(BusinessException.class, () -> storage.put(KEY, new byte[] {1}, "image/png"));
        assertEquals(1, storage.compensationFailureCount());
        assertFalse(output.getAll().contains("private-sdk-secret"));
        assertFalse(output.getAll().contains("private-delete-secret"));
        assertTrue(output.getAll().contains(KEY));
    }

    @Test
    void invalidStaticConfigurationAndOutsidePrefixAreRejected() {
        var storage = storage("public-read");
        assertThrows(IllegalArgumentException.class, () -> storage.delete("outside/x.png"));
        for (String domain :
                java.util.List.of(
                        "http://example.com",
                        "https://user:secret@example.com",
                        "https://example.com/path",
                        "https://example.com?token=x")) {
            var settings = new OssProperties();
            settings.setPublicDomain(domain);
            assertThrows(IllegalArgumentException.class, settings::validate);
        }
        properties.validate();
    }
}
