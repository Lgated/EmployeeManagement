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
import com.example.empmgmt.dto.response.UserWithEmployeeDTO;
import com.example.empmgmt.service.ExportService;
import com.example.empmgmt.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final ExportService exportService;

    public UserController(UserService userService, ExportService exportService) {
        this.exportService = exportService;
        this.userService = userService;
    }

    /**
     * 获取用户列表（仅超级管理员）
     * 支持分页和筛选
     */
    @GetMapping
    @RequiresRole("SUPER_ADMIN")
    public Result<PageResponse<UserResponse>> list(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<UserResponse> userResponsePageResponse = userService.pageQuery(username, role, enabled, page, size);
        return Result.success(userResponsePageResponse);
    }

    /**
     * 获取单个用户详情（仅超级管理员）
     */
    @GetMapping("/{id}")
    @RequiresRole("SUPER_ADMIN")
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

    /**
     * 场景1：用户管理页面显示员工信息
     * 获取用户列表（包含员工详细信息）
     */
    @GetMapping("/with-employee")
    @RequiresRole("SUPER_ADMIN")
    public Result<PageResponse<UserWithEmployeeDTO>> listWithEmployee(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<UserWithEmployeeDTO> result = userService.pageQueryWithEmployee(
                username, role, enabled, page, size
        );
        return Result.success(result);
    }

    /**
     * 场景2：根据员工信息查找用户账号
     * 根据员工姓名查找用户
     */
    @GetMapping("/by-employee-name/{employeeName}")
    @RequiresRole("SUPER_ADMIN")
    public Result<UserResponse> getByEmployeeName(@PathVariable String employeeName) {
        UserResponse user = userService.findByEmployeeName(employeeName);
        return Result.success(user);
    }

    /**
     * 场景4：员工离职时检查用户账号
     * 根据员工ID查找关联的用户
     */
    @GetMapping("/by-employee-id/{employeeId}")
    @RequiresRole("SUPER_ADMIN")
    public Result<List<UserResponse>> getByEmployeeId(@PathVariable Long employeeId) {
        List<UserResponse> users = userService.findUsersByEmployeeId(employeeId);
        return Result.success(users);
    }

    /**
     * 场景4：员工离职处理
     * 禁用员工关联的所有用户账号
     */
    @PutMapping("/handle-resignation/{employeeId}")
    @RequiresRole("SUPER_ADMIN")
    @OperationLog(
            module = "USER",
            type = OperationType.UPDATE,
            description = "处理员工离职，禁用关联用户账号"
    )
    public Result<Void> handleEmployeeResignation(@PathVariable Long employeeId) {
        userService.handleEmployeeResignation(employeeId);
        return Result.success("处理成功", null);
    }

    /**
     * 场景5：创建用户时自动关联员工信息
     * 创建用户并加载员工信息
     */
    @PostMapping("/with-employee")
    @RequiresRole("SUPER_ADMIN")
    @OperationLog(
            module = "USER",
            type = OperationType.CREATE,
            description = "创建用户并加载员工信息",
            saveResult = true
    )
    public Result<UserResponse> createWithEmployee(@Valid @RequestBody UserCreateRequest request) {
        UserResponse user = userService.createWithEmployee(request);
        return Result.success("创建成功", user);
    }

    /**
     * 导出用户信息为Excel
     * 权限：仅SUPER_ADMIN
     */
    @GetMapping("/export")
    @RequiresRole("SUPER_ADMIN")
    public void exportUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String department,
            HttpServletResponse response
    ) throws IOException {
        exportService.exportUsersToExcel(role, department, response);
    }

}
