package com.example.empmgmt.service;

import com.example.empmgmt.domain.User;
import com.example.empmgmt.dto.request.LoginRequest;
import com.example.empmgmt.dto.request.RegisterRequest;
import com.example.empmgmt.dto.request.UserCreateRequest;
import com.example.empmgmt.dto.request.UserUpdateRequest;
import com.example.empmgmt.dto.response.AuthResponse;
import com.example.empmgmt.dto.response.PageResponse;
import com.example.empmgmt.dto.response.UserResponse;
import com.example.empmgmt.dto.response.UserWithEmployeeDTO;

import java.util.List;

public interface UserService {

    //注册
    AuthResponse register(RegisterRequest request);

    //登录
    AuthResponse login(LoginRequest request);

    /**
     * 用户登录并返回 User 对象（由 Controller 生成双 Token）
     */
    User loginAndGetUser(LoginRequest request);

    //分页查询用户列表
    PageResponse<UserResponse> pageQuery(String name,String role,Boolean enabled,int page,int size);

    //根据id查询用户
    UserResponse findById(Long id);

    //创建用户
    UserResponse create(UserCreateRequest request);

    //更新用户信息
    UserResponse update(Long id, UserUpdateRequest request);

    //删除用户
    void delete(Long id);

    //启用|禁用用户
    void updateStatus(Long id,Boolean enabled);

    //分配角色
    UserResponse assignRole(Long id,String role,String department,Long employeeId);

    //重置密码
    void resetPassword(Long id,String newPassword);

    /**
     * 场景：用户管理页面显示员工信息
     * 分页查询用户列表（包含员工详细信息）
     */
    PageResponse<UserWithEmployeeDTO> pageQueryWithEmployee(
            String username, String role, Boolean enabled, int page, int size
    );

    /**
     * 场景2：根据员工信息查找用户账号
     * 根据员工姓名查找用户
     */
    UserResponse findByEmployeeName(String employeeName);

    /**
     * 场景4：员工离职时检查用户账号
     * 根据员工ID查找关联的用户
     */
    List<UserResponse> findUsersByEmployeeId(Long employeeId);

    /**
     * 场景5：创建用户时自动关联员工信息
     * 创建用户并加载员工信息
     */
    UserResponse createWithEmployee(UserCreateRequest request);

    /**
     * 场景6：员工离职时处理用户账号
     * 处理员工离职相关的用户账号
     */
    void handleEmployeeResignation(Long employeeId);

    /**
     * 将UserResponse转换为User实体
     * @param byId
     * @return
     */
    User toEntity(UserResponse byId);
}
