package com.maplehush.admin.system.user.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户管理安全输出；不包含密码、密码哈希、授权版本或 refresh 凭据
 *
 * @param id 用户 ID，使用字符串避免前端精度丢失
 * @param username 不可变的登录账号
 * @param avatar 可空的长期公开头像 URL
 * @param name 用户姓名，对应 real_name
 * @param deptId 所属部门 ID；未分配时为空
 * @param roleIds 已关联角色 ID；包括停用角色供管理页识别
 * @param superAdmin 按内置角色关联判断的只读保护标识；不依赖账号名或固定 ID
 * @param status 1 启用、0 停用
 * @param remark 管理员备注，不是本人 introduction
 * @param createTime 东八区秒级创建时间
 * @param updateTime 东八区秒级更新时间
 */
public record SysUserVO(
        String id,
        String username,
        String avatar,
        String name,
        String deptId,
        List<String> roleIds,
        boolean superAdmin,
        Integer status,
        String remark,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
