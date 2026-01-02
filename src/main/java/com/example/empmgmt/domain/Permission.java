package com.example.empmgmt.domain;


import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "permission")
public class Permission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 100)
    private String code;  // 权限代码：employee:create

    @Column(nullable = false, length = 100)
    private String name;  // 权限名称

    @Column(nullable = false, length = 100)
    private String resource;  // 资源：employee/user/log

    @Column(nullable = false, length = 50)
    private String action;  // 操作：create/read/update/delete

    @Column(length = 500)
    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

}
