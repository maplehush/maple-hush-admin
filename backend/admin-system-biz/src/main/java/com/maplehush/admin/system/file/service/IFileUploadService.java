package com.maplehush.admin.system.file.service;

import com.maplehush.admin.system.file.vo.FileUploadVO;

import org.springframework.web.multipart.MultipartFile;

/**
 * 统一上传和业务绑定凭据；首版白名单为公开图片，不改动任何用户资料
 */
public interface IFileUploadService {
    /**
     * 上传白名单文件并生成属于当前用户的短期绑定标识
     * @param file 单个上传文件
     * @return 标识、公开 URL 与净化后的文件信息
     */
    FileUploadVO upload(MultipartFile file);

    /**
     * 在业务事务中校验上传标识的时效和归属，不消费以支持表单失败重试
     * @param uploadId 随机 32 位小写十六进制标识
     * @param uploaderId 由认证上下文决定的操作人 ID
     * @return 已验证图片的长期 URL
     */
    String resolveImage(String uploadId, long uploaderId);
}
