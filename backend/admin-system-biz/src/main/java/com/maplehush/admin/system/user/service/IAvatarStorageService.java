package com.maplehush.admin.system.user.service;

/**
 * 本人头像对象存储边界；仅接受已经验证并重新编码的 PNG
 */
public interface IAvatarStorageService {
    /**
     * 上传随机对象，失败时清理可能已落地的新对象
     * @param userId 当前用户 ID，不由客户端指定
     * @param png 已移除元数据的 PNG 字节
     * @return 长期 HTTPS 公开 URL
     */
    String upload(long userId, byte[] png);

    /**
     * 清理受控域名/前缀且属于当前用户的旧对象，失败计数并记录补偿键
     * @param userId 当前用户 ID
     * @param url 待清理头像 URL；不受控或为空时忽略
     */
    void deleteOwned(long userId, String url);
}
