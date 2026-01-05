package com.example.empmgmt.service.Impl;

import com.example.empmgmt.common.Exception.BusinessException;
import com.example.empmgmt.common.util.SecurityUtil;
import com.example.empmgmt.domain.User;
import com.example.empmgmt.dto.request.LoginRequest;
import com.example.empmgmt.dto.request.RegisterRequest;
import com.example.empmgmt.dto.request.UserCreateRequest;
import com.example.empmgmt.dto.request.UserUpdateRequest;
import com.example.empmgmt.dto.response.AuthResponse;
import com.example.empmgmt.dto.response.PageResponse;
import com.example.empmgmt.dto.response.UserResponse;
import com.example.empmgmt.repository.UserRepository;
import com.example.empmgmt.service.UserService;
import com.example.empmgmt.common.util.JwtUtil;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


@Slf4j
@Service
public class UserServiceImpl implements UserService {


    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    // 默认密码
    private static final String DEFAULT_PASSWORD = "123456";

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
        String token = jwtUtil.generateToken(user.getUsername(),user.getId());
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
        String token = jwtUtil.generateToken(user.getUsername(),user.getId());
        return AuthResponse.of(token,jwtUtil.getExpirationTime());
    }

    //TODO:不懂
    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> pageQuery(String username, String role, Boolean enabled, int page, int size) {

        //创建分页对象（按创建时间倒叙）
        Pageable pageable = PageRequest.of(page - 1, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        // 动态条件查询
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 用户名模糊查询
            if (username != null && !username.trim().isEmpty()) {
                predicates.add(cb.like(root.get("username"), "%" + username + "%"));
            }

            // 角色精确查询
            if (role != null && !role.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("role"), role));
            }

            // 启用状态查询
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<User> userPage = userRepository.findAll(spec, pageable);
        //转换为响应DTO
        List<UserResponse> records = userPage.getContent().stream()
                .map(UserResponse::fromEntity)
                .collect(Collectors.toList());

        // 使用你的 PageResponse 格式
        return new PageResponse<>(
                records,
                userPage.getTotalElements(),
                page,  // 当前页（从1开始）
                size   // 每页大小
        );
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        User user = userRepository.findById(id).orElseThrow(() ->
                new IllegalArgumentException("用户不存在: " + id));
        return UserResponse.fromEntity(user);
    }

    @Override
    @Transactional
    public UserResponse create(UserCreateRequest request) {
        // 验证用户名唯一性
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new BusinessException("用户名已存在");
        }

        // 验证角色和部门的关系
        validateRoleAndDepartment(request.getRole(), request.getDepartment());
        // 创建用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setEmail(request.getEmail());
        user.setRole(request.getRole());
        user.setDepartment(request.getDepartment());
        user.setEmployeeId(request.getEmployeeId());
        user.setEnabled(true);

        User savedUser = userRepository.save(user);
        log.info("创建用户成功: {}, 角色: {}", savedUser.getUsername(), savedUser.getRole());

        return UserResponse.fromEntity(savedUser);

    }

    @Override
    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request) {
        User user = userRepository.findById(id).orElseThrow(() ->
                new IllegalArgumentException("用户不存在: " + id));

        // 更新字段
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getDepartment() != null) {
            user.setDepartment(request.getDepartment());
        }
        if (request.getEmployeeId() != null) {
            user.setEmployeeId(request.getEmployeeId());
        }

        User updatedUser = userRepository.save(user);
        log.info("更新用户成功: {}", updatedUser.getUsername());

        return UserResponse.fromEntity(updatedUser);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        // 不能删除超级管理员（可选）
        if ("SUPER_ADMIN".equals(user.getRole())) {
            throw new BusinessException("不能删除超级管理员账号");
        }

        userRepository.delete(user);
        log.info("删除用户成功: {}", user.getUsername());
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Boolean enabled) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));

         // 不能禁用自己（通过当前用户ID判断）
         Long currentUserId = SecurityUtil.getCurrentUserId();
         if (user.getId().equals(currentUserId)) {
             throw new BusinessException("不能禁用自己的账号");
         }

        user.setEnabled(enabled);
        userRepository.save(user);

        log.info("更新用户状态: {}, 启用: {}", user.getUsername(), enabled);
    }

    @Override
    @Transactional
    public UserResponse assignRole(Long id, String role, String department, Long employeeId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        // 验证角色和部门的关系
        validateRoleAndDepartment(role, department);

        // 更新角色
        user.setRole(role);
        user.setDepartment(department);
        user.setEmployeeId(employeeId);

        User updatedUser = userRepository.save(user);
        log.info("分配角色成功: {}, 新角色: {}", updatedUser.getUsername(), role);

        return UserResponse.fromEntity(updatedUser);
    }

    @Override
    public void resetPassword(Long id, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        log.info("重置密码成功: {}", user.getUsername());
    }

    private void validateRoleAndDepartment(String role, String department) {
        if ("MANAGER".equals(role) && (department == null || department.trim().isEmpty())) {
            throw new BusinessException("部门经理必须指定部门");
        }
    }

}
