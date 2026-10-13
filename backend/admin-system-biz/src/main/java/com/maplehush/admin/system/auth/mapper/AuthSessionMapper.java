package com.maplehush.admin.system.auth.mapper;

import com.maplehush.admin.system.auth.entity.AuthRefreshSession;
import com.maplehush.admin.system.user.entity.SysUser;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 会话写入统一先锁用户再锁会话，避免改密与轮换使用相反锁顺序。 */
@Mapper
public interface AuthSessionMapper {
    @Select("SELECT id,user_id,auth_version,family_id,token_hash,expire_time,used_time,revoked_time "
            + "FROM auth_refresh_session WHERE token_hash=#{hash}")
    AuthRefreshSession findByHash(@Param("hash") String hash);

    @Select("SELECT id,user_id,auth_version,family_id,token_hash,expire_time,used_time,revoked_time "
            + "FROM auth_refresh_session WHERE id=#{id} FOR UPDATE")
    AuthRefreshSession lockSession(@Param("id") long id);

    @Select("SELECT id,username,real_name,avatar_url,introduction,status,must_change_password,auth_version "
            + "FROM sys_user WHERE id=#{id} FOR UPDATE")
    SysUser lockUser(@Param("id") long id);

    @Select("SELECT r.role_code FROM sys_role r JOIN sys_user_role ur ON ur.role_id=r.id "
            + "WHERE ur.user_id=#{userId} AND r.status=1 ORDER BY r.id")
    List<String> roleCodes(@Param("userId") long userId);

    @Insert("INSERT INTO auth_refresh_session (user_id,auth_version,family_id,token_hash,expire_time) "
            + "VALUES (#{userId},#{authVersion},#{familyId},#{tokenHash},#{expireTime})")
    int insert(AuthRefreshSession session);

    @Update("UPDATE auth_refresh_session SET used_time=#{now} WHERE id=#{id} "
            + "AND used_time IS NULL AND revoked_time IS NULL")
    int markUsed(@Param("id") long id, @Param("now") LocalDateTime now);

    @Update("UPDATE auth_refresh_session SET revoked_time=#{now} WHERE family_id=#{family} AND revoked_time IS NULL")
    int revokeFamily(@Param("family") String family, @Param("now") LocalDateTime now);

    @Delete("DELETE FROM auth_refresh_session WHERE expire_time < #{cutoff} LIMIT 1000")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
