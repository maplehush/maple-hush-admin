package com.maplehush.admin.system.user.controller;

import com.maplehush.admin.common.api.Result;
import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.system.auth.security.AuthCookies;
import com.maplehush.admin.system.user.dto.UserPasswordDTO;
import com.maplehush.admin.system.user.dto.UserProfileDTO;
import com.maplehush.admin.system.user.service.IUserProfileService;
import com.maplehush.admin.system.user.vo.UserProfileVO;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.Map;
import java.util.Set;

/**
 * 本人资料入口，普通已登录用户即可使用，不依赖系统用户管理权限
 */
@RestController
@RequestMapping("/api/user")
public class UserProfileController {
    private final IUserProfileService service;
    private final AuthCookies cookies;
    private final CsrfTokenRepository csrf;

    public UserProfileController(
            IUserProfileService service, AuthCookies cookies, CsrfTokenRepository csrf) {
        this.service = service;
        this.cookies = cookies;
        this.csrf = csrf;
    }

    /**
     * 更新姓名和简介
     * @param dto 资料全量表单
     * @return 当前用户资料
     */
    @PutMapping("/profile")
    public Result<UserProfileVO> profile(@Valid @RequestBody UserProfileDTO dto) {
        return Result.ok(service.updateProfile(dto));
    }

    /**
     * 本人改密成功后清 Cookie，前端丢弃 access 并跳登录页
     * @param dto 旧密码与新密码
     * @param request 当前请求，用于清理 CSRF 凭据
     * @param response 当前响应，写出清 Cookie 头
     * @return 空成功体，不发新登录凭据
     */
    @PutMapping("/password")
    public Result<Void> password(
            @Valid @RequestBody UserPasswordDTO dto,
            HttpServletRequest request,
            HttpServletResponse response) {
        service.changePassword(dto);
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.clearRefresh().toString());
        csrf.saveToken(null, request, response);
        return Result.ok(null);
    }

    /**
     * 上传且仅上传一个 file 字段，拒绝客户端覆盖用户或对象 URL
     * @param request multipart 请求，不接受额外普通字段或文件字段
     * @return 长期头像 URL
     */
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, String>> avatar(MultipartHttpServletRequest request) {
        if (!request.getParameterMap().isEmpty()
                || !request.getMultiFileMap().keySet().equals(Set.of("file"))
                || request.getMultiFileMap().get("file").size() != 1) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "头像请求只接受一个 file 文件");
        }
        return Result.ok(Map.of("avatar", service.updateAvatar(request.getFile("file"))));
    }
}
