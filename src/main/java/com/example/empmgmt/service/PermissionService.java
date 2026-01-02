package com.example.empmgmt.service;

import java.util.Set;

public interface PermissionService {


    /**
     * 检查用户是否有指定权限
     */
    boolean hasPermission(Long userId, String permissionCode);

    /**
     * 检查用户是否有指定角色
     */
    boolean hasRole(Long userId, String roleCode);

    /**
     * 获取用户的所有权限代码
     */
    Set<String> getUserPermissions(Long userId);

    /**
     * 检查用户是否可以访问指定部门的数据
     */
    boolean canAccessDepartment(Long userId, String department);

    /**
     * 检查用户是否可以访问指定员工数据
     */
    boolean canAccessEmployee(Long userId, Long employeeId);
}
