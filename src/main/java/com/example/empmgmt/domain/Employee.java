package com.example.empmgmt.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
public class Employee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String gender;
    private Integer age;
    private String department;
    private String position;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    private BigDecimal salary;

    @Column(name = "avatar", length = 500)
    private String avatar;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    //实体被更新到数据库之前（即 UPDATE SQL 发送前），由 JPA 引擎自动调用被标注的方法
    //自动更新更新时间
    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    /**
     * 反向关联：一个员工可能对应多个用户账号
     *
     * @OneToMany: 一对多关系
     * mappedBy = "employee": 指定User实体中的employee字段作为关联的拥有方
     * fetch = FetchType.LAZY: 懒加载，避免N+1查询
     * cascade = CascadeType.ALL: 级联操作（可选，根据业务需求决定） // TODO: 什么是级联操作？是否需要级联操作？
     */
    @OneToMany(mappedBy = "employee", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<User> users = new ArrayList<>();

}
