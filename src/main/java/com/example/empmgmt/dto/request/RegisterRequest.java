package com.example.empmgmt.dto.request;

import jakarta.validation.constraints.*;

//注册请求
public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 50, message = "用户名长度必须在3-50之间")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 100, message = "密码长度必须在6-100之间")
        String password,

        @Email(message = "邮箱格式不正确")
        String email
) {
}
