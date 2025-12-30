package com.example.empmgmt.util;

import com.example.empmgmt.security.UserAuthentication;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 *  从 Spring Security上下文中拿取当前登录的信息
 */
public class SecurityUtil {

    /**
     * 拿当前用户主键
     */
    public static Long getCurrentUserId(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication != null && authentication instanceof UserAuthentication){
            return ((UserAuthentication) authentication).getUserId();
        }
        return null;
    }

    /**
     * 拿当前登录名
     */
    public static String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof String) {
            return (String) authentication.getPrincipal();
        }
        return null;
    }

    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }
}
