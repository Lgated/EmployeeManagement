package com.example.empmgmt.dto.response;

//认证响应
public record AuthResponse(
        String token,
        String tokenType,
        Long expiresIn
) {
    public static AuthResponse of(String token, Long expiresIn) {
        return new AuthResponse(token, "Bearer", expiresIn);
    }
}
