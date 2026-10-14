package com.maplehush.admin.system.permission.handler;

import com.maplehush.admin.common.api.Result;
import com.maplehush.admin.common.exception.ErrorCode;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 方法授权失败需优先映射为 403，不能落入通用异常的 500 分支。 */
@Order(-1)
@RestControllerAdvice
public class PermissionExceptionHandler {
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> forbidden(AccessDeniedException exception) {
        return ResponseEntity.status(403).body(Result.fail(ErrorCode.FORBIDDEN.code(), ErrorCode.FORBIDDEN.value(), "没有操作权限"));
    }
}
