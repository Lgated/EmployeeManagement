package com.example.empmgmt.controller;

import com.example.empmgmt.common.annotation.RequiresRole;
import com.example.empmgmt.dto.request.UserCreateRequest;
import com.example.empmgmt.dto.response.Result;
import com.example.empmgmt.dto.response.UserResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    /**
     * 获取用户列表（仅超级管理员）
     */
    @GetMapping
    @RequiresRole("SUPER_ADMIN")
    public Result<List<UserResponse>> list() {
        // 实现逻辑
        return Result.success(null);
    }

    /**
     * 创建用户（仅超级管理员）
     */
    @PostMapping
    @RequiresRole("SUPER_ADMIN")
    public Result<UserResponse> create(@RequestBody UserCreateRequest request) {
        // 实现逻辑
        return Result.success(null);
    }

    /**
     * 分配角色（仅超级管理员）
     */
    @PutMapping("/{id}/role")
    @RequiresRole("SUPER_ADMIN")
    public Result<Void> assignRole(
            @PathVariable Long id,
            @RequestParam String role
    ) {
        // 实现逻辑
        return Result.success(null);
    }


}
