package com.example.empmgmt.dto.response;

//认证响应
public record AuthResponse(
        String token,
        String tokenType,
        Long expiresIn,
        String username,
        String role,
        String department,
        Long employeeId
) {


    /**
     * 创建认证响应（包含完整用户信息）
     */
    public static AuthResponse of(
            String token,
            Long expiresIn,
            String username,
            String role,
            String department,
            Long employeeId
    ) {
        return new AuthResponse(
                token,
                "Bearer",
                expiresIn,
                username,
                role,
                department,
                employeeId
        );
    }
    /**
     * 兼容旧方法（向后兼容）
     */
    public static AuthResponse of(String token, Long expiresIn) {
        return new AuthResponse(token, "Bearer", expiresIn, null, null, null, null);
    }
}
