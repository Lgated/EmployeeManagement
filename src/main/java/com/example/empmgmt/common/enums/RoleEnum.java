package com.example.empmgmt.common.enums;

import lombok.Getter;

/**
 * 角色枚举
 */
@Getter
public enum RoleEnum {
    SUPER_ADMIN("SUPER_ADMIN", "超级管理员", 1),
    MANAGER("MANAGER", "部门经理", 2),
    EMPLOYEE("EMPLOYEE", "普通员工", 3);

    private final String code;
    private final String name;
    private final int level;

    RoleEnum(String code, String name, int level) {
        this.code = code;
        this.name = name;
        this.level = level;
    }

    /**
     *  通过代码获取角色枚举
     */
    public static RoleEnum fromCode(String code) {
        for (RoleEnum role : RoleEnum.values()) {
            if (role.getCode().equals(code)) {
                return role;
            }
        }
        throw new IllegalArgumentException("未知的角色代码: " + code);
    }

    /**
     * 判断是否有更高权限
     */
    public boolean hasHigherLevelThan(RoleEnum other) {
        return this.level < other.level;  // level 越小权限越高
    }

}
