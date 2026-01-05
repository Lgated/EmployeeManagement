package com.example.empmgmt.service;

import com.example.empmgmt.dto.request.LoginRequest;
import com.example.empmgmt.dto.request.RegisterRequest;
import com.example.empmgmt.dto.request.UserCreateRequest;
import com.example.empmgmt.dto.request.UserUpdateRequest;
import com.example.empmgmt.dto.response.AuthResponse;
import com.example.empmgmt.dto.response.PageResponse;
import com.example.empmgmt.dto.response.UserResponse;

public interface UserService {

    //注册
    AuthResponse register(RegisterRequest request);

    //登录
    AuthResponse login(LoginRequest request);

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
}
