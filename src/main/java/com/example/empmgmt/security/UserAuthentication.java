package com.example.empmgmt.security;

import lombok.Getter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * 继承 UsernamePasswordAuthenticationToken 只是给 Spring Security 的令牌多加一个 userId 字段，
 *  任何地方都能一键拿到当前用户主键，不用层层强转 UserDetails。
 */
@Getter
public class UserAuthentication extends UsernamePasswordAuthenticationToken {

    private final Long userId;
    private final String role;
    private final String department;
    private final Long employeeId;

    // principal -> 用户名/UserDetails对象 || credentials -> 密码（通常后面会清空）
    //authorities： 权限集合
    /**
     * 构造函数
     * @param principal 用户名
     * @param credentials 密码（通常为 null）
     * @param authorities 权限集合
     * @param userId 用户ID
     * @param role 用户角色
     * @param department 用户部门
     * @param employeeId 关联的员工ID
     */
    public UserAuthentication(
            Object principal,
            Object credentials,
            Collection<? extends GrantedAuthority> authorities,
            Long userId,
            String role,
            String department,
            Long employeeId
    ) {
        super(principal, credentials, authorities);
        this.userId = userId;
        this.role = role;
        this.department = department;
        this.employeeId = employeeId;
    }


}
