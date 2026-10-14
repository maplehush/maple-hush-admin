package com.maplehush.admin.system.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.maplehush.admin.common.entity.BaseEntity;

import lombok.Getter;
import lombok.Setter;

/**
 * 系统用户；密码字段只保存单向哈希，不直接作为 HTTP 出参
 */
@Getter
@Setter
@TableName("sys_user")
public class SysUser extends BaseEntity {
    /**
     * 数据库主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户所属部门 ID；未分配时为空
     */
    private Long deptId;

    /**
     * 登录账号
     */
    private String username;

    /**
     * 用户真实姓名
     */
    private String realName;

    /**
     * BCrypt 密码哈希；禁止返回客户端或写入日志
     */
    @TableField(select = false)
    private String password;

    /**
     * 头像的长期 HTTPS 地址，不保存临时签名 URL
     */
    private String avatarUrl;

    /**
     * 用户个人简介
     */
    private String introduction;

    /**
     * 启用状态：1 启用，0 停用
     */
    private Integer status;

    /**
     * 是否必须先改密；为 true 时不能签发普通登录会话
     */
    private Boolean mustChangePassword;

    /**
     * 授权版本；权限或密码变更后递增，用于使旧令牌失效
     */
    private Long authVersion;

    /**
     * 业务备注
     */
    private String remark;
}
