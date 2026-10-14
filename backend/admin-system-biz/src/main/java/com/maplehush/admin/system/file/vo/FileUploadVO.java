package com.maplehush.admin.system.file.vo;

/**
 * 统一上传结果；素材 URL 长期有效，绑定标识有时效且属于上传人
 * @param uploadId 随机上传标识，2 小时内可由上传人用于业务表单
 * @param name 原文件基础名称，仅用于展示，不作为 OSS 路径
 * @param url 长期公开 HTTPS URL
 * @param contentType 服务端净化后的真实类型，首版 image/png
 * @param size 净化后对象字节数
 */
public record FileUploadVO(
        String uploadId, String name, String url, String contentType, long size) {}
