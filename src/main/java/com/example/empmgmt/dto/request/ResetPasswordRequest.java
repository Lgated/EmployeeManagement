package com.example.empmgmt.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;


/**
 * 重置密码请求DTO
 */
@Data
public class ResetPasswordRequest {
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度必须在6-20之间")
    private String newPassword;
}
