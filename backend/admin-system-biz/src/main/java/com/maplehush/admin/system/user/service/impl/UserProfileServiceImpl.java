package com.maplehush.admin.system.user.service.impl;

import com.maplehush.admin.common.annotation.OperationLog;
import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.system.auth.service.IAuthRedisService;
import com.maplehush.admin.system.auth.support.PasswordPolicy;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

/**
 * 本人资料事务；锁用户后复核认证版本，头像网络上传在短数据库事务之前执行
 */
@Service
public class UserProfileServiceImpl implements IUserProfileService {
    private static final Logger LOG = LoggerFactory.getLogger(UserProfileServiceImpl.class);

    /**
     * 原始及重新编码头像的字节上限：2 MiB
     */
    private static final int MAX_BYTES = 2 * 1024 * 1024;

    private final SysUserMapper users;
    private final IPermissionService permissions;
    private final IAuthorizationChangeService changes;
    private final IAuthRedisService redis;
    private final PasswordEncoder encoder;
    private final PasswordPolicy passwords;
    private final IAvatarStorageService storage;
    private final TransactionTemplate transactions;

    public UserProfileServiceImpl(
            SysUserMapper users,
            IPermissionService permissions,
            IAuthorizationChangeService changes,
            IAuthRedisService redis,
            PasswordEncoder encoder,
            PasswordPolicy passwords,
            IAvatarStorageService storage,
            PlatformTransactionManager manager) {
        this.users = users;
        this.permissions = permissions;
        this.changes = changes;
        this.redis = redis;
        this.encoder = encoder;
        this.passwords = passwords;
        this.storage = storage;
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
        byte[] png = normalize(file);
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

    private byte[] normalize(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw invalid("头像须为不超过 2 MiB 的 JPEG、PNG 或 WebP");
        }
        try (var stream = file.getInputStream();
                var input = new MemoryCacheImageInputStream(stream)) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalid("头像实际内容不是支持的图片");
            }
            var reader = readers.next();
            try {
                if (!Set.of("png", "jpeg", "jpg", "webp")
                        .contains(reader.getFormatName().toLowerCase(Locale.ROOT))) {
                    throw invalid("头像格式不受支持");
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1
                        || height < 1
                        || width > 4096
                        || height > 4096
                        || (long) width * height > 16000000) {
                    throw invalid("头像尺寸超出限制");
                }
                var image = reader.read(0);
                var output = new ByteArrayOutputStream();
                if (image == null
                        || !ImageIO.write(image, "png", output)
                        || output.size() > MAX_BYTES) {
                    throw invalid("头像解码或重新编码失败，请使用较小图片");
                }
                return output.toByteArray();
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw invalid("头像内容无效或损坏");
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
    }
}
