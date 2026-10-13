package com.maplehush.admin.common.exception;

import com.maplehush.admin.common.api.Result;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 将可预期错误映射为稳定响应，隐藏未知错误细节。 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException exception) {
        ErrorCode error = exception.getErrorCode();
        return ResponseEntity.status(error.status()).body(Result.fail(error.code(), error.value(), exception.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public ResponseEntity<Result<Void>> handleInvalid(Exception exception) {
        return ResponseEntity.badRequest().body(Result.fail(ErrorCode.BAD_REQUEST.code(), ErrorCode.BAD_REQUEST.value(), "请求参数不合法"));
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<Result<Void>> handleNotFound(Exception exception) {
        return ResponseEntity.status(ErrorCode.NOT_FOUND.status()).body(Result.fail(ErrorCode.NOT_FOUND.code(),
                ErrorCode.NOT_FOUND.value(), "请求的资源不存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnknown(Exception exception) {
        LOG.error("Unhandled request failure", exception);
        return ResponseEntity.internalServerError().body(Result.fail(ErrorCode.INTERNAL_ERROR.code(),
                ErrorCode.INTERNAL_ERROR.value(), "服务器暂时无法处理请求"));
    }
}
