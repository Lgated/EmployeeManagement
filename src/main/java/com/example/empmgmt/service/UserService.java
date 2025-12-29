package com.example.empmgmt.service;

import com.example.empmgmt.dto.request.LoginRequest;
import com.example.empmgmt.dto.request.RegisterRequest;
import com.example.empmgmt.dto.response.AuthResponse;

public interface UserService {

    //注册
    AuthResponse register(RegisterRequest request);

    //登录
    AuthResponse login(LoginRequest request);

}
