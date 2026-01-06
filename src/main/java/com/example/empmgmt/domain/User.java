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

    // ========== 改造点1：添加JPA关联注解 ==========
    /**
     * 关联的员工对象（使用JPA关联）
     *
     * @ManyToOne: 多对一关系，多个用户可能关联同一员工
     * fetch = FetchType.LAZY: 懒加载，避免N+1查询问题
     * optional = true: 允许为空（SUPER_ADMIN和MANAGER可能没有关联员工）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "employee_id",                    // 数据库外键列名
            referencedColumnName = "id",             // 引用的Employee表的主键
            foreignKey = @ForeignKey(                 // 可选：定义外键约束名称
                    name = "fk_user_employee",
                    value = ConstraintMode.CONSTRAINT
            )
    )
    private Employee employee;  // 关联的员工对象

    // ========== 改造点2：保留employeeId字段用于快速访问 ==========
    /**
     * 员工ID（冗余字段，用于快速访问，避免加载整个Employee对象）
     *
     * insertable = false: 插入时不能手动设置（由employee对象自动设置）
     * updatable = false: 更新时不能手动设置（由employee对象自动设置）
     *
     * 注意：这个字段的值会通过employee对象自动同步
     */
    @Column(name = "employee_id", insertable = false, updatable = false)
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
