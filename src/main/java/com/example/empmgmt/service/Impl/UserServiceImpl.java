package com.example.empmgmt.service.Impl;

import com.example.empmgmt.common.Exception.BusinessException;
import com.example.empmgmt.common.util.SecurityUtil;
import com.example.empmgmt.domain.Employee;
import com.example.empmgmt.domain.User;
import com.example.empmgmt.dto.request.LoginRequest;
import com.example.empmgmt.dto.request.RegisterRequest;
import com.example.empmgmt.dto.request.UserCreateRequest;
import com.example.empmgmt.dto.request.UserUpdateRequest;
import com.example.empmgmt.dto.response.AuthResponse;
import com.example.empmgmt.dto.response.PageResponse;
import com.example.empmgmt.dto.response.UserResponse;
import com.example.empmgmt.dto.response.UserWithEmployeeDTO;
import com.example.empmgmt.repository.EmployeeRepository;
import com.example.empmgmt.repository.UserRepository;
import com.example.empmgmt.service.EmployeeService;
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
    private final EmployeeRepository employeeRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    // 默认密码
    private static final String DEFAULT_PASSWORD = "123456";

    public UserServiceImpl(UserRepository userRepository,
                           JwtUtil jwtUtil,
                           PasswordEncoder passwordEncoder,
                           EmployeeRepository employeeRepository){
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.employeeRepository = employeeRepository;
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
        String token = jwtUtil.generateToken(user);
        return AuthResponse.of(
                token,
                jwtUtil.getExpirationTime(),
                user.getUsername(),
                user.getRole(),
                user.getDepartment(),
                user.getEmployeeId()
        );
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
        String token = jwtUtil.generateToken(user);
        return AuthResponse.of(
                token,
                jwtUtil.getExpirationTime(),
                user.getUsername(),
                user.getRole(),
                user.getDepartment(),
                user.getEmployeeId()
        );
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

        //设置关联员工信息（如果提供了employeeId）
        if (request.getEmployeeId() != null) {
            // 查找员工对象
            Employee employee = employeeRepository.findByIdAndDeletedFalse(request.getEmployeeId())
                    .orElseThrow(() -> new BusinessException("员工不存在或已删除: " + request.getEmployeeId()));

            // 设置关联（会自动设置employeeId字段）
            user.setEmployee(employee);
        }
        User savedUser = userRepository.save(user);
        log.info("创建用户成功: {}, 角色: {}, 员工ID: {}",
                savedUser.getUsername(), savedUser.getRole(), savedUser.getEmployeeId());


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

        // 更新关联员工信息（如果提供了employeeId）
        if (request.getEmployeeId() != null) {
            if (request.getEmployeeId() == 0) {
                // 如果传入0，表示解除关联
                user.setEmployee(null);
            } else {
                // 查找员工对象
                Employee employee = employeeRepository.findByIdAndDeletedFalse(request.getEmployeeId())
                        .orElseThrow(() -> new BusinessException("员工不存在或已删除: " + request.getEmployeeId()));

                // 设置关联
                user.setEmployee(employee);
            }
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

        // ========== 改造点：设置employee关联 ==========
        if (employeeId != null) {
            Employee employee = employeeRepository.findByIdAndDeletedFalse(employeeId)
                    .orElseThrow(() -> new BusinessException("员工不存在或已删除: " + employeeId));
            user.setEmployee(employee);
        } else {
            user.setEmployee(null);
        }

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

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserWithEmployeeDTO> pageQueryWithEmployee(String username, String role, Boolean enabled, int page, int size) {
        // 创建分页对象
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        // 先查询符合条件的用户ID（分页）
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (username != null && !username.trim().isEmpty()) {
                predicates.add(cb.like(root.get("username"), "%" + username + "%"));
            }
            if (role != null && !role.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<User> userPage = userRepository.findAll(spec, pageable);
        List<Long> userIds = userPage.getContent().stream()
                .map(User::getId)
                .collect(Collectors.toList());

        // 使用自定义查询方法批量加载employee信息
        // 需要在UserRepository中添加：
        // @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.employee e WHERE u.id IN :ids")
        // List<User> findAllByIdsWithEmployee(@Param("ids") List<Long> ids);

        // 或者使用循环加载（性能较差，但简单）
        List<UserWithEmployeeDTO> records = userPage.getContent().stream()
                .map(user -> {
                    // 触发懒加载（在事务内）
                    if (user.getEmployee() != null) {
                        user.getEmployee().getName(); // 触发加载
                    }
                    return UserWithEmployeeDTO.fromEntity(user);
                })
                .collect(Collectors.toList());

        return new PageResponse<>(
                records,
                userPage.getTotalElements(),
                page,
                size
        );
    }

    @Override
    public UserResponse findByEmployeeName(String employeeName) {
        User user = userRepository.findByEmployeeName(employeeName)
                .orElseThrow(() -> new BusinessException("该员工没有关联的用户账号: " + employeeName));

        return UserResponse.fromEntity(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> findUsersByEmployeeId(Long employeeId) {
        // 查找关联的用户
        List<User> users = userRepository.findByEmployeeId(employeeId);
        return users.stream()
                .map(UserResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public UserResponse createWithEmployee(UserCreateRequest request) {
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
        user.setEnabled(true);

        // 设置employee关联
        if (request.getEmployeeId() != null) {
            Employee employee = employeeRepository.findByIdAndDeletedFalse(request.getEmployeeId())
                    .orElseThrow(() -> new BusinessException("员工不存在或已删除: " + request.getEmployeeId()));
            user.setEmployee(employee);
        }

        User savedUser = userRepository.save(user);

        // 刷新实体以加载关联的employee（如果需要）
        userRepository.flush();
        savedUser = userRepository.findById(savedUser.getId()).orElse(savedUser);

        log.info("创建用户成功: {}, 角色: {}, 员工: {}",
                savedUser.getUsername(),
                savedUser.getRole(),
                savedUser.getEmployee() != null ? savedUser.getEmployee().getName() : "无");

        return UserResponse.fromEntity(savedUser);
    }

    /**
     * 场景：员工离职时检查用户账号
     * 根据员工ID查找关联的用户，并禁用账号
     */
    @Override
    @Transactional
    public void handleEmployeeResignation(Long employeeId) {
        // 查找关联的用户
        List<User> users = userRepository.findByEmployeeId(employeeId);

        if (!users.isEmpty()) {
            // 禁用所有关联的用户账号
            users.forEach(user -> {
                user.setEnabled(false);
                userRepository.save(user);
                log.info("员工 {} 离职，已禁用用户账号: {}", employeeId, user.getUsername());
            });
        } else {
            log.info("员工 {} 离职，没有关联的用户账号", employeeId);
        }
    }

    private void validateRoleAndDepartment(String role, String department) {
        if ("MANAGER".equals(role) && (department == null || department.trim().isEmpty())) {
            throw new BusinessException("部门经理必须指定部门");
        }
    }

}
