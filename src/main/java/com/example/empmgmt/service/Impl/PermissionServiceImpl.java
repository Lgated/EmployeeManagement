package com.example.empmgmt.service.Impl;

import com.example.empmgmt.common.enums.RoleEnum;
import com.example.empmgmt.domain.User;
import com.example.empmgmt.repository.EmployeeRepository;
import com.example.empmgmt.repository.UserRepository;
import com.example.empmgmt.service.PermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
@Slf4j
public class PermissionServiceImpl implements PermissionService {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;

    public PermissionServiceImpl(UserRepository userRepository,
                                 EmployeeRepository employeeRepository) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
    }

    @Override
    public boolean hasPermission(Long userId, String permissionCode) {
        User user = userRepository.findById(userId).orElseThrow(() ->
                new IllegalArgumentException("用户不存在: " + userId));

        // 超级管理员拥有所有权限
        if ("SUPER_ADMIN".equals(user.getRole())) {
            return true;
        }

        // 根据角色判断权限
        return checkPermissionByRole(RoleEnum.fromCode(user.getRole()), permissionCode);
    }

    @Override
    public boolean hasRole(Long userId, String roleCode) {
        User user = userRepository.findById(userId).orElseThrow(() ->
                new IllegalArgumentException("用户不存在: " + userId));

        return roleCode.equals(user.getRole());
    }

    @Override
    public Set<String> getUserPermissions(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() ->
                new IllegalArgumentException("用户不存在: " + userId));

        return getPermissionsByRole(RoleEnum.fromCode(user.getRole()));

    }

    @Override
    public boolean canAccessDepartment(Long userId, String department) {
        User user = userRepository.findById(userId).orElseThrow(() ->
                new IllegalArgumentException("用户不存在: " + userId));

        RoleEnum role = RoleEnum.fromCode(user.getRole());

        // 超级管理员可以访问所有部门
        if (role == RoleEnum.SUPER_ADMIN) {
            return true;
        }

        // 部门经理只能访问自己部门的数据
        if (role == RoleEnum.MANAGER) {
            return department.equals(user.getDepartment());
        }

        // 普通员工不能访问其他部门的数据
        return false;
    }

    @Override
    public boolean canAccessEmployee(Long userId, Long employeeId) {
        User user = userRepository.findById(userId).orElseThrow(() ->
                new IllegalArgumentException("用户不存在: " + userId));

        RoleEnum role = RoleEnum.fromCode(user.getRole());

        // 超级管理员可以访问所有员工
        if (role == RoleEnum.SUPER_ADMIN) {
            return true;
        }

        // 部门经理可以访问自己部门的员工
        if (role == RoleEnum.MANAGER) {
            String managerDept = user.getDepartment();
            String employeeDept = employeeRepository.findById(employeeId)
                    .map(emp -> emp.getDepartment())
                    .orElse(null);
            return managerDept != null && managerDept.equals(employeeDept);
        }

        // 普通员工只能访问自己的数据
        if (role == RoleEnum.EMPLOYEE) {
            return employeeId.equals(user.getEmployeeId());
        }

        return false;
    }

    /**
     * 根据角色检查是否有所需权限
     */
    private boolean checkPermissionByRole(RoleEnum role, String permissionCode) {
        Set<String> permissions = getPermissionsByRole(role);
        return permissions.contains(permissionCode);
    }

    /**
     * 获取角色的权限集合
     */
    private Set<String> getPermissionsByRole(RoleEnum role) {
        Set<String> permissions = new HashSet<>();

        //switch (role) 里写 枚举常量名 是 Java 的标准语法糖，编译器会自动把它翻译成 role.ordinal() 的整型比较
        switch (role) {
            case SUPER_ADMIN:
                // 超级管理员拥有所有权限
                permissions.add("employee:create");
                permissions.add("employee:read");
                permissions.add("employee:update");
                permissions.add("employee:delete");
                permissions.add("employee:export");
                permissions.add("user:create");
                permissions.add("user:read");
                permissions.add("user:update");
                permissions.add("user:delete");
                permissions.add("user:assign_role");
                permissions.add("log:read");
                permissions.add("log:export");
                permissions.add("stats:read");
                break;

            case MANAGER:
                // 部门经理权限
                permissions.add("employee:create");
                permissions.add("employee:read");
                permissions.add("employee:update");
                permissions.add("employee:delete");
                permissions.add("employee:export");
                permissions.add("log:read");
                permissions.add("stats:read");
                break;

            case EMPLOYEE:
                // 普通员工权限
                permissions.add("employee:read");
                permissions.add("employee:update");  // 仅限个人信息
                permissions.add("log:read");         // 仅限个人日志
                break;
        }

        return permissions;
    }
}
