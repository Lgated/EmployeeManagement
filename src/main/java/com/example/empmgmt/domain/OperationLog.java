package com.example.empmgmt.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.LocalDateTime;

@Entity
@Table(name = "operation_log")
@Data
@Slf4j
public class OperationLog {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "operation_type", nullable = false, length = 20)
    private String operationType;

    @Column(name = "module", nullable = false, length = 50)
    private String module;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "method", length = 200)
    private String method;

    @Column(name = "params", columnDefinition = "TEXT")
    private String params;

    @Column(name = "result", columnDefinition = "TEXT")
    private String result;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "execution_time")
    private Long executionTime;

    @Column(name = "status", length = 20)
    private String status = "SUCCESS";

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();


}


