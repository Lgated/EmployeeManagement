package com.example.empmgmt.domain;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "export_task")
public class ExportTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_type", nullable = false, length = 50)
    private String taskType;       // EMPLOYEE_EXPORT / USER_EXPORT

    @Column(name = "params", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON) // 让 Hibernate 将 String 转换为 JSONB。
    private String params;         // 直接存 JSON 字符串

    @Column(name = "status", nullable = false, length = 20)
    private String status;         // PENDING / PROCESSING / SUCCESS / FAILED

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "error_msg")
    private String errorMsg;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
