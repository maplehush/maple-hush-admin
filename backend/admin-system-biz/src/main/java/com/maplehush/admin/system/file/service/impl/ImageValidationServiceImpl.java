package com.maplehush.admin.system.file.service.impl;

import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.system.file.service.IImageValidationService;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

/**
 * 检查实际格式和尺寸，统一净化为 PNG，不使用客户端文件名或 MIME 判断内容
 */
@Service
public class ImageValidationServiceImpl implements IImageValidationService {
    /**
     * 原始及重新编码结果最大 2 MiB
     */
    private static final int MAX_BYTES = 2 * 1024 * 1024;

    @Override
    public byte[] normalize(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw invalid("头像须为不超过 2 MiB 的 JPEG、PNG 或 WebP");
        }
        try (var stream = file.getInputStream();
                var input = new MemoryCacheImageInputStream(stream)) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalid("头像实际内容不是支持的图片");
            }
            var reader = readers.next();
            try {
                if (!Set.of("png", "jpeg", "jpg", "webp")
                        .contains(reader.getFormatName().toLowerCase(Locale.ROOT))) {
                    throw invalid("头像格式不受支持");
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1
                        || height < 1
                        || width > 4096
                        || height > 4096
                        || (long) width * height > 16000000) {
                    throw invalid("头像尺寸超出限制");
                }
                var image = reader.read(0);
                var output = new ByteArrayOutputStream();
                if (image == null
                        || !ImageIO.write(image, "png", output)
                        || output.size() > MAX_BYTES) {
                    throw invalid("头像解码或重新编码失败，请使用较小图片");
                }
                return output.toByteArray();
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw invalid("头像内容无效或损坏");
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
    }
}
