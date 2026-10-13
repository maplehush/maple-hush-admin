package com.maplehush.admin.system.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.maplehush.admin.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 系统用户；密码字段只保存单向哈希，不直接作为 HTTP 出参。 */
@Getter
@Setter
@TableName("sys_user")
public class SysUser extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deptId;
    private String username;
    private String realName;
    private String password;
    private String avatarUrl;
    private String introduction;
    private Integer status;
    private Boolean mustChangePassword;
    private Long authVersion;
    private String remark;
}
