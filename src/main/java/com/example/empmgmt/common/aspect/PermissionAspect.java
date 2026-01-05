package com.example.empmgmt.common.aspect;

import com.example.empmgmt.common.Exception.PermissionDeniedException;
import com.example.empmgmt.common.annotation.RequiresPermission;
import com.example.empmgmt.common.annotation.RequiresRole;
import com.example.empmgmt.common.util.SecurityUtil;
import com.example.empmgmt.service.PermissionService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 权限切面
 * 用于处理权限检查逻辑
 */

@Aspect
@Slf4j
@Component
public class PermissionAspect {


    private final PermissionService permissionService;

    public PermissionAspect(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /**
     * 检查权限注解
     */
    @Before("@annotation(com.example.empmgmt.common.annotation.RequiresPermission)")
    public void checkPermission(JoinPoint joinPoint){
        Long userId = SecurityUtil.getCurrentUserId();
        // 添加调试日志
        log.debug("当前用户ID: {}", userId);
        log.debug("当前认证信息: {}", SecurityContextHolder.getContext().getAuthentication());

        // 获取方法签名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        // 获取方法上的注解
        Method method = signature.getMethod();
        // 取得注解信息
        RequiresPermission annotation = method.getAnnotation(RequiresPermission.class);

        // 获取权限代码
        String permissionCode = annotation.value();

        // 检查基本权限
        if (!permissionService.hasPermission(userId, permissionCode)) {
            log.warn("用户 {} 没有权限 {}", userId, permissionCode);
            throw new PermissionDeniedException("没有权限执行此操作");
        }

        // 检查部门权限
        if (annotation.checkDepartment()) {
            checkDepartmentPermission(joinPoint, userId);
        }

        // 检查所有者权限
        if (annotation.checkOwner()) {
            checkOwnerPermission(joinPoint, userId);
        }
    }

    /**
     * 检查角色注解
     */
    @Before("@annotation(com.example.empmgmt.common.annotation.RequiresRole)")
    public void checkRole(JoinPoint joinPoint) {
        Long userId = SecurityUtil.getCurrentUserId();

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        // 取得注解信息
        RequiresRole annotation = method.getAnnotation(RequiresRole.class);

        // 获取角色列表
        String[] roles = annotation.value();

        boolean hasRole = false;

        for (String role : roles) {
            if (permissionService.hasRole(userId, role)) {
                hasRole = true;
                break;
            }
        }

        if (!hasRole) {
            log.warn("用户 {} 没有所需角色", userId);
            throw new PermissionDeniedException("没有权限执行此操作");
        }
    }

    /**
     * 检查部门权限（部门经理只能操作本部门）
     */
    private void checkDepartmentPermission(JoinPoint joinPoint, Long userId) {
        Object[] args = joinPoint.getArgs();

        // 尝试从参数中提取部门信息
        for (Object arg : args) {
            if (arg instanceof String && isDepartmentField(arg.toString())) {
                String department = arg.toString();
                if (!permissionService.canAccessDepartment(userId, department)) {
                    throw new PermissionDeniedException("只能操作本部门的数据");
                }
            }
        }
    }

    /**
     * 检查所有者权限（普通员工只能操作自己）
     */
    private void checkOwnerPermission(JoinPoint joinPoint, Long userId) {
        Object[] args = joinPoint.getArgs();

        // 尝试从参数中提取员工ID
        for (Object arg : args) {
            if (arg instanceof Long) {
                Long employeeId = (Long) arg;
                if (!permissionService.canAccessEmployee(userId, employeeId)) {
                    throw new PermissionDeniedException("只能操作自己的数据");
                }
            }
        }
    }


    /**
     * 判断是否为部门字段
     */
    private boolean isDepartmentField(String value) {
        // 简单判断，实际应该更精确
        return value.matches("^[\\u4e00-\\u9fa5]{2,10}部$");
    }
}
