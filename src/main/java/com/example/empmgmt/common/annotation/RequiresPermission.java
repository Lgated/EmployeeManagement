package com.example.empmgmt.common.annotation;


import java.lang.annotation.*;

/**
 * 权限检查注解
 * 用于方法级别的权限控制
 *
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresPermission {

    /**
     * 需要的权限代码
     */
    String value();

    /**
     * 是否检查部门权限（部门经理只能操作本部门数据）
     */
    boolean checkDepartment() default false;

    /**
     * 是否检查数据所有者（普通员工只能操作自己的数据）
     */
    boolean checkOwner() default false;

}
