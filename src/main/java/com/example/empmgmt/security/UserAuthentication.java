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

    public UserAuthentication(Object principal, Object credentials,
                              // principal -> 用户名/UserDetails对象 || credentials -> 密码（通常后面会清空）
            Collection<? extends GrantedAuthority> authorities // 权限集合
            ,Long userId) {
        super(principal, credentials,authorities);
        this.userId = userId;
    }

}
