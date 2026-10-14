package com.maplehush.admin.system.file.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 共享图片校验和净化，统一上传与本人头像使用同一规则
 */
public interface IImageValidationService {
    /**
     * 校验真实 JPEG/PNG/WebP，移除元数据并重新编码为 PNG
     * @param file 不可信上传文件，原始/结果不超过 2 MiB
     * @return 已验证的 PNG 内容
     */
    byte[] normalize(MultipartFile file);
}
