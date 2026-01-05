package com.example.empmgmt.common.util;

import com.example.empmgmt.security.UserAuthentication;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

/**
 *  从 Spring Security上下文中拿取当前登录的信息
 */
public class SecurityUtil {

    /**
     * 获取当前用户ID
     */
    public static Long getCurrentUserId(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // 从 UserAuthentication 对象直接获取
        if (authentication instanceof UserAuthentication) {
            return ((UserAuthentication) authentication).getUserId();
        }

        return null;
    }

    /**
     * 获取当前登录用户名
     */
    public static String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof UserAuthentication) {
            return authentication.getName();  // getName() 返回 principal
        }

        return null;
    }

    /**
     * 获取当前用户角色
     */
    public static String getCurrentUserRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof UserAuthentication) {
            return ((UserAuthentication) authentication).getRole();
        }

        return null;
    }

    /**
     * 获取当前用户部门
     */
    public static String getCurrentUserDepartment() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof UserAuthentication) {
            return ((UserAuthentication) authentication).getDepartment();
        }

        return null;
    }

    /**
     * 获取当前用户关联的员工ID
     */
    public static Long getCurrentEmployeeId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof UserAuthentication) {
            return ((UserAuthentication) authentication).getEmployeeId();
        }

        return null;
    }

    /**
     * 判断是否已认证
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }
}
