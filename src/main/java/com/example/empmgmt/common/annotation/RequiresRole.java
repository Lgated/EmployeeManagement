package com.example.empmgmt.common.annotation;

import java.lang.annotation.*;

/**
 * 角色检查注解
 * 用于方法级别的角色控制
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresRole {
    /**
     * 需要的角色名称（满足其中一个即可）
     */
    String[] value();
}
