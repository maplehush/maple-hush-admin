package com.maplehush.admin.system.user.service;

import com.maplehush.admin.system.user.dto.UserPasswordDTO;
import com.maplehush.admin.system.user.dto.UserProfileDTO;
import com.maplehush.admin.system.user.vo.UserProfileVO;

import org.springframework.web.multipart.MultipartFile;

/**
 * 当前登录用户的资料、密码和头像，不接受客户端指定目标身份
 */
public interface IUserProfileService {
    /**
     * 修改姓名/简介，提交后清快照，保留当前会话
     * @param dto 本人资料表单
     * @return 本人资料结果
     */
    UserProfileVO updateProfile(UserProfileDTO dto);

    /**
     * 验证旧密码后改密，使本人全部会话失效
     * @param dto 只用于本次校验和哈希的密码表单
     */
    void changePassword(UserPasswordDTO dto);

    /**
     * 验证并重新编码头像后上传，数据库失败补偿新对象
     * @param file 不可信原始文件，不使用文件名决定路径或格式
     * @return 长期公开 HTTPS URL
     */
    String updateAvatar(MultipartFile file);
}
