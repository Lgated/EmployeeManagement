package com.example.empmgmt.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "user_account")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    private String email;

    // 新增字段
    @Column(name = "role", length = 50)
    private String role = "EMPLOYEE";  // 默认为普通员工

    @Column(name = "department", length = 100)
    private String department;  // 部门（部门经理使用）

    @Column(name = "employee_id")
    private Long employeeId;  // 关联的员工ID（员工角色使用）

    @Column(name = "enabled")
    private Boolean enabled = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
