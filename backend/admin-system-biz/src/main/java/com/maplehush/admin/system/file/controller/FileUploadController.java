package com.maplehush.admin.system.file.controller;

import com.maplehush.admin.common.api.Result;
import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.system.file.service.IFileUploadService;
import com.maplehush.admin.system.file.vo.FileUploadVO;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.Set;

/**
 * 系统统一文件上传入口，首版按公开图片白名单校验，不直接修改业务资料
 */
@RestController
@RequestMapping("/api/files")
public class FileUploadController {
    private final IFileUploadService service;

    public FileUploadController(IFileUploadService service) {
        this.service = service;
    }

    /**
     * 已登录用户上传单个 file，后续业务表单使用返回的 uploadId
     * @param request 只含一个 file 的 multipart 请求
     * @return 上传标识和文件元数据
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<FileUploadVO> upload(MultipartHttpServletRequest request) {
        if (!request.getParameterMap().isEmpty()
                || !request.getMultiFileMap().keySet().equals(Set.of("file"))
                || request.getMultiFileMap().get("file").size() != 1) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "文件上传只接受一个 file 字段");
        }
        return Result.ok(service.upload(request.getFile("file")));
    }
}
