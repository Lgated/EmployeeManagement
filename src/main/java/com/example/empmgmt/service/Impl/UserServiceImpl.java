package com.example.empmgmt.service.Impl;

import com.example.empmgmt.domain.User;
import com.example.empmgmt.dto.request.LoginRequest;
import com.example.empmgmt.dto.request.RegisterRequest;
import com.example.empmgmt.dto.response.AuthResponse;
import com.example.empmgmt.repository.UserRepository;
import com.example.empmgmt.service.UserService;
import com.example.empmgmt.util.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository,
                       JwtUtil jwtUtil,
                       PasswordEncoder passwordEncoder){
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public AuthResponse register(RegisterRequest request) {

        // 检查用户名是否已存在
        if(userRepository.findByUsername(request.username()).isPresent()){
            throw new IllegalArgumentException("用户名已存在");
        }

        //创建新用户
        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setEmail(request.email());
        user.setEnabled(true);
        userRepository.save(user);

        //生成token
        String token = jwtUtil.generateToken(user.getUsername());
        return AuthResponse.of(token, jwtUtil.getExpirationTime());
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {

        // 查找用户
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));

        // 校验密码
        if(!passwordEncoder.matches(request.password(),user.getPassword())){
            throw new IllegalArgumentException("用户名或密码错误");
        }

        // 检查用户是否启用
        if(!user.getEnabled()){
            throw new IllegalArgumentException("用户已被禁用");
        }

        // 生成token
        String token = jwtUtil.generateToken(user.getUsername());
        return AuthResponse.of(token,jwtUtil.getExpirationTime());
    }
}
