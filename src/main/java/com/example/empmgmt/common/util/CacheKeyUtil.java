package com.example.empmgmt.common.util;

/**
 * Redis 缓存 Key 生成工具
 * 统一管理 Key 格式，减少硬编码字符串
 */
public class CacheKeyUtil {

    /**
     * 员工分页列表 key
     */
    public static String buildEmployeeListKey(String name, String department, int page, int size) {
        String safeName = name == null ? "" : name.trim(); // trim() : 把字符串首尾的所有“空白字符”去掉
        String safeDept = department == null ? "" : department.trim();
        return String.format("employee:list:%s:%s:%d:%d", safeName, safeDept, page, size);
    }

    /**
     * 用户分页列表 key
     */
    public static String buildUserListKey(String username,String role, int page, int size) {
        String safeUsername = username == null ? "" : username.trim();
        String safeRole = role == null ? "" : role.trim();
        return String.format("user:list:%s:%s:%d:%d", safeUsername,safeRole, page, size);
    }

    /**
     * 员工列表 key 集合，用于记录所有分页 key，方便统一删除
     */
    public static String employeeListKeySet() {
        return "employee:list:keys";
    }

    /**
     * 用户列表 key 集合
     */
    public static String userListKeySet() {
        return "user:list:keys";
    }
}
