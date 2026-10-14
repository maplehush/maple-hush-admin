package com.maplehush.admin.system.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 首次或重置后的改密表单。 */
public record InitialPasswordDTO(@NotBlank @Size(max = 128) String newPassword) {}
