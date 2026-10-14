package com.maplehush.admin.system.user.service.impl;

import com.maplehush.admin.common.annotation.OperationLog;
import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.system.auth.service.IAuthRedisService;
import com.maplehush.admin.system.auth.support.PasswordPolicy;
import com.maplehush.admin.system.file.service.IImageValidationService;
import com.maplehush.admin.system.permission.service.IAuthorizationChangeService;
import com.maplehush.admin.system.permission.service.IPermissionService;
import com.maplehush.admin.system.user.dto.UserPasswordDTO;
import com.maplehush.admin.system.user.dto.UserProfileDTO;
import com.maplehush.admin.system.user.entity.SysUser;
import com.maplehush.admin.system.user.mapper.SysUserMapper;
import com.maplehush.admin.system.user.service.IAvatarStorageService;
import com.maplehush.admin.system.user.service.IUserProfileService;
import com.maplehush.admin.system.user.vo.UserProfileVO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;

/**
 * 本人资料事务；锁用户后复核认证版本，头像网络上传在短数据库事务之前执行
 */
@Service
public class UserProfileServiceImpl implements IUserProfileService {
    private static final Logger LOG = LoggerFactory.getLogger(UserProfileServiceImpl.class);

    private final SysUserMapper users;
    private final IPermissionService permissions;
    private final IAuthorizationChangeService changes;
    private final IAuthRedisService redis;
    private final PasswordEncoder encoder;
    private final PasswordPolicy passwords;
    private final IAvatarStorageService storage;
    private final IImageValidationService images;
    private final TransactionTemplate transactions;

    public UserProfileServiceImpl(
            SysUserMapper users,
            IPermissionService permissions,
            IAuthorizationChangeService changes,
            IAuthRedisService redis,
            PasswordEncoder encoder,
            PasswordPolicy passwords,
            IAvatarStorageService storage,
            IImageValidationService images,
            PlatformTransactionManager manager) {
        this.users = users;
        this.permissions = permissions;
        this.changes = changes;
        this.redis = redis;
        this.encoder = encoder;
        this.passwords = passwords;
        this.storage = storage;
        this.images = images;
        this.transactions = new TransactionTemplate(manager);
    }

    @Override
    @Transactional
    @OperationLog(value = "user:profile:update", targetType = "self")
    public UserProfileVO updateProfile(UserProfileDTO dto) {
        SysUser user = lockSelf();
        String introduction =
                dto.introduction() == null || dto.introduction().isBlank()
                        ? null
                        : dto.introduction().strip();
        if (users.updateSelfProfile(user.getId(), dto.realName().strip(), introduction) != 1) {
            throw new IllegalStateException("Profile was not updated");
        }
        changes.profileChanged(user.getId());
        return new UserProfileVO(user.getId().toString(), dto.realName().strip(), introduction);
    }

    @Override
    @Transactional
    @OperationLog(value = "user:password:update", targetType = "self")
    public void changePassword(UserPasswordDTO dto) {
        long userId = permissions.current().user().getId();
        redis.checkPasswordChangeLimit(userId);
        SysUser user = lockSelf();
        if (!encoder.matches(dto.oldPassword(), user.getPassword())) {
            throw invalid("旧密码错误");
        }
        try {
            passwords.validateNew(dto.newPassword(), dto.oldPassword());
        } catch (IllegalArgumentException exception) {
            throw invalid(exception.getMessage());
        }
        if (users.changeOwnPassword(userId, encoder.encode(dto.newPassword())) != 1) {
            throw new IllegalStateException("Password was not updated");
        }
        changes.usersChanged(List.of(userId));
        users.revokeSessions(userId);
    }

    @Override
    @OperationLog(value = "user:avatar:update", targetType = "self")
    public String updateAvatar(MultipartFile file) {
        long userId = permissions.current().user().getId();
        byte[] png = images.normalize(file);
        String next = storage.upload(userId, png);
        String previous;
        try {
            previous =
                    transactions.execute(
                            status -> {
                                SysUser user = lockSelf();
                                if (users.updateOwnAvatar(userId, next) != 1) {
                                    throw new IllegalStateException("Avatar was not updated");
                                }
                                changes.profileChanged(userId);
                                return user.getAvatarUrl();
                            });
        } catch (RuntimeException exception) {
            storage.deleteOwned(userId, next);
            LOG.error(
                    "Avatar database update failed userId={} errorType={}",
                    userId,
                    exception.getClass().getSimpleName());
            if (exception instanceof BusinessException business) {
                throw business;
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "头像保存失败，请稍后重试");
        }
        if (!Objects.equals(previous, next)) {
            storage.deleteOwned(userId, previous);
        }
        return next;
    }

    private SysUser lockSelf() {
        var context = permissions.current();
        SysUser user = users.lockSelf(context.user().getId());
        if (user == null
                || user.getStatus() != 1
                || Boolean.TRUE.equals(user.getMustChangePassword())
                || !Objects.equals(user.getAuthVersion(), context.user().getAuthVersion())) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "登录状态已变化，请重新登录");
        }
        return user;
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
    }
}
