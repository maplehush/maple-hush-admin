package com.maplehush.admin.system.role.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 角色表单；创建时要求 roleCode，修改时只允许回传原编码。null permissions 表示保留。 */
public record SysRoleSaveDTO(@Pattern(regexp = "[a-z][a-z0-9_]{0,63}") String roleCode,
        @NotBlank @Size(max = 100) String name, @Min(0) @Max(1) Integer status,
        @Size(max = 500) String remark, @Size(max = 100) List<String> permissions) {}
