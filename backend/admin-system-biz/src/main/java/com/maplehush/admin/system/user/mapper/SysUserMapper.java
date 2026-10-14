package com.maplehush.admin.system.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.maplehush.admin.system.user.entity.SysUser;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户表基础映射；业务读取须控制密码字段暴露
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
    /**
     * 锁定管理目标的安全字段，密码哈希不参与读取
     * @param id 用户 ID
     * @return 用户状态和资料；不存在时为空
     */
    @Select(
            "SELECT id,dept_id,username,real_name,status,must_change_password,auth_version,remark,create_time,update_time FROM sys_user WHERE id=#{id} FOR UPDATE")
    SysUser lockUser(@Param("id") long id);

    /**
     * 全量更新管理资料，显式写入 null 以清除部门和备注，不修改账号、简介和头像
     * @param user 仅使用 id、realName、deptId、status、remark 字段
     * @return 实际更新行数
     */
    @Update(
            "UPDATE sys_user SET real_name=#{realName},dept_id=#{deptId},status=#{status},remark=#{remark},update_time=CURRENT_TIMESTAMP WHERE id=#{id}")
    int updateProfile(SysUser user);

    /**
     * 保存临时密码哈希并标记必须改密，版本与会话由同一服务事务处理
     * @param id 目标用户 ID
     * @param hash 已经 BCrypt 编码的密码哈希，不能传入明文
     * @return 实际更新行数
     */
    @Update(
            "UPDATE sys_user SET password=#{hash},must_change_password=1,update_time=CURRENT_TIMESTAMP WHERE id=#{id}")
    int resetPassword(@Param("id") long id, @Param("hash") String hash);

    /**
     * 撤销目标用户全部尚未撤销的会话族，不影响操作管理员
     * @param id 目标用户 ID
     * @return 本次撤销记录数，可为 0
     */
    @Update(
            "UPDATE auth_refresh_session SET revoked_time=CURRENT_TIMESTAMP WHERE user_id=#{id} AND revoked_time IS NULL")
    int revokeSessions(@Param("id") long id);

    /**
     * 删除用户前清理 refresh 外键关联，不删除任何登录或操作日志
     * @param id 即将删除的用户 ID
     * @return 清理的 refresh 记录数
     */
    @Delete("DELETE FROM auth_refresh_session WHERE user_id=#{id}")
    int removeSessions(@Param("id") long id);

    /**
     * 本人写操作的锁定投影；密码哈希只用于旧密码核验，不返回客户端
     * @param id 认证上下文用户 ID
     * @return 当前用户的写入校验字段
     */
    @Select(
            "SELECT id,username,password,real_name,introduction,avatar_url,status,must_change_password,auth_version FROM sys_user WHERE id=#{id} FOR UPDATE")
    SysUser lockSelf(@Param("id") long id);

    /**
     * 只写本人姓名和简介
     * @param id 当前用户 ID
     * @param name 姓名
     * @param introduction 可空简介
     * @return 更新行数
     */
    @Update(
            "UPDATE sys_user SET real_name=#{name},introduction=#{introduction},update_time=CURRENT_TIMESTAMP WHERE id=#{id}")
    int updateSelfProfile(
            @Param("id") long id,
            @Param("name") String name,
            @Param("introduction") String introduction);

    /**
     * 保存本人新密码哈希，不设置临时密码或再次强制改密
     * @param id 当前用户 ID
     * @param hash 新密码的 BCrypt 哈希
     * @return 更新行数
     */
    @Update(
            "UPDATE sys_user SET password=#{hash},must_change_password=0,update_time=CURRENT_TIMESTAMP WHERE id=#{id}")
    int changeOwnPassword(@Param("id") long id, @Param("hash") String hash);

    /**
     * 保存后端生成的头像 URL
     * @param id 当前用户 ID
     * @param url 受控的长期公开 URL
     * @return 更新行数
     */
    @Update("UPDATE sys_user SET avatar_url=#{url},update_time=CURRENT_TIMESTAMP WHERE id=#{id}")
    int updateOwnAvatar(@Param("id") long id, @Param("url") String url);
}
