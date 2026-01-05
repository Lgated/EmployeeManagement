package com.example.empmgmt.controller;

import com.example.empmgmt.common.annotation.OperationLog;
import com.example.empmgmt.common.annotation.RequiresRole;
import com.example.empmgmt.common.enums.OperationType;
import com.example.empmgmt.domain.User;
import com.example.empmgmt.dto.request.AssignRoleRequest;
import com.example.empmgmt.dto.request.ResetPasswordRequest;
import com.example.empmgmt.dto.request.UserCreateRequest;
import com.example.empmgmt.dto.request.UserUpdateRequest;
import com.example.empmgmt.dto.response.PageResponse;
import com.example.empmgmt.dto.response.Result;
import com.example.empmgmt.dto.response.UserResponse;
import com.example.empmgmt.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 获取用户列表（仅超级管理员）
     * 支持分页和筛选
     */
    @OperationLog(
            module = "USER",
            type = OperationType.QUERY,
            description = "查询用户列表"
    )
    @GetMapping
    @RequiresRole("SUPER_ADMIN")
    public Result<List<UserResponse>> list(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<UserResponse> userResponsePageResponse = userService.pageQuery(username, role, enabled, page, size);
        return Result.success(userResponsePageResponse.records());
    }

    /**
     * 获取单个用户详情（仅超级管理员）
     */
    @GetMapping("/{id}")
    @RequiresRole("SUPER_ADMIN")
    @OperationLog(
            module = "USER",
            type = OperationType.QUERY,
            description = "查询用户详情"
    )
    public Result<UserResponse> getById(@PathVariable Long id) {
        UserResponse user = userService.findById(id);
        return Result.success(user);
    }

    /**
     * 创建用户（仅超级管理员）
     */
    @PostMapping
    @RequiresRole("SUPER_ADMIN")
    @OperationLog(
            module = "USER",
            type = OperationType.CREATE,
            description = "创建用户",
            saveResult = true
    )
    public Result<UserResponse> create(@RequestBody UserCreateRequest request) {
        UserResponse user = userService.create(request);
        return Result.success("创建成功", user);
    }

    /**
     * 更新用户信息（仅超级管理员）
     */
    @PutMapping("/{id}")
    @RequiresRole("SUPER_ADMIN")
    @OperationLog(
            module = "USER",
            type = OperationType.UPDATE,
            description = "更新用户信息",
            saveResult = true
    )
    public Result<UserResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UserUpdateRequest request
    ) {
        UserResponse updatedUser = userService.update(id, request);
        return Result.success("更新成功", updatedUser);
    }

    /**
     * 启用/禁用用户（仅超级管理员）
     */
    @PutMapping("/{id}/status")
    @RequiresRole("SUPER_ADMIN")
    @OperationLog(
            module = "USER",
            type = OperationType.UPDATE,
            description = "更新用户状态"
    )
    public Result<Void> updateStatus(
            @PathVariable Long id,
            @RequestParam Boolean enabled
    ) {
        userService.updateStatus(id, enabled);
        String action = enabled ? "启用" : "禁用";
        return Result.success(action + "成功", null);
    }

    /**
     * 分配角色（仅超级管理员）
     */
    @PutMapping("/{id}/role")
    @RequiresRole("SUPER_ADMIN")
    @OperationLog(
            module = "USER",
            type = OperationType.UPDATE,
            description = "分配用户角色",
            saveResult = true
    )
    public Result<UserResponse> assignRole(
            @PathVariable Long id,
            @Valid @RequestBody AssignRoleRequest request
    ) {
        UserResponse user = userService.assignRole(
                id,
                request.getRole(),
                request.getDepartment(),
                request.getEmployeeId()
        );
        return Result.success("分配角色成功", user);
    }


    /**
     * 删除用户（仅超级管理员）
     */
    @DeleteMapping("/{id}")
    @RequiresRole("SUPER_ADMIN")
    @OperationLog(
            module = "USER",
            type = OperationType.DELETE,
            description = "删除用户"
    )
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.success("删除成功", null);
    }

    /**
     * 重置用户密码（仅超级管理员）
     */
    @PutMapping("/{id}/password")
    @RequiresRole("SUPER_ADMIN")
    @OperationLog(
            module = "USER",
            type = OperationType.UPDATE,
            description = "重置用户密码"
    )
    public Result<Void> resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        userService.resetPassword(id, request.getNewPassword());
        return Result.success("重置密码成功", null);
    }

}
