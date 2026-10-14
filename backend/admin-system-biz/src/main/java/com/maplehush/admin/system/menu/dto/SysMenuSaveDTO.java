package com.maplehush.admin.system.menu.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

/** 管理表单采用完整路径；title/icon/order 由 meta 映射至独立列。 */
public record SysMenuSaveDTO(String pid,
        @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]{0,99}") String name,
        @NotBlank String type, @Size(max = 255) String path, @Size(max = 255) String component,
        @Size(max = 255) String redirect, @Size(max = 120) String authCode,
        @Min(0) @Max(1) Integer status, Map<String, Object> meta) {}
