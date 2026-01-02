package com.example.empmgmt.common.enums;

import lombok.Getter;

/**
 * 操作类型枚举
 */
@Getter
public enum OperationType {

    CREATE("新增"),
    UPDATE("修改"),
    DELETE("删除"),
    QUERY("查询"),
    EXPORT("导出"),
    IMPORT("导入"),
    LOGIN("登录"),
    LOGOUT("登出"),
    OTHER("其他");

    private final String description;

    OperationType(String description) {
        this.description = description;
    }

}
