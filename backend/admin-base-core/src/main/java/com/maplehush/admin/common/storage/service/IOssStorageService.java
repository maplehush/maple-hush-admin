package com.maplehush.admin.common.storage.service;

/**
 * 受控 OSS 存储基础能力，不判断用户身份和业务资料
 */
public interface IOssStorageService {
    /**
     * 写入公共读 Bucket；PUT 失败时补偿可能已经写入的对象
     * @param key 服务端生成的受控对象键
     * @param bytes 完整且已校验的对象内容
     * @param contentType 服务端确定的内容类型
     */
    void put(String key, byte[] bytes, String contentType);

    /**
     * 删除受控前缀下的对象；调用方负责业务归属校验
     * @param key 服务端提取的对象键
     */
    void delete(String key);

    /**
     * 获取 PUT 失败后补偿删除失败次数
     * @return 当前进程累计失败数
     */
    long compensationFailureCount();
}
