package com.maplehush.admin.system.dept.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 部门全量表单；空父 ID 或字符串 0 表示根部门。 */
public record SysDeptSaveDTO(@Size(max = 19) String pid, @NotBlank @Size(max = 100) String name,
                             Integer sort, @Min(0) @Max(1) Integer status, @Size(max = 500) String remark) {}
