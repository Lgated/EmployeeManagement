package com.example.empmgmt.common.annotation;


import com.example.empmgmt.common.enums.OperationType;

import java.lang.annotation.*;

/**
 * 操作日志注解
 * 使用示例：@OperationLog(module = "员工管理", type = OperationType.CREATE, description = "创建员工")
 */
//自定义注解只能写在 方法 上
@Target(ElementType.METHOD)
//限定生命周期
//→ 注解会被 保留到 JVM 运行期，因此 反射/AOP 可以读到它。
@Retention(RetentionPolicy.RUNTIME)
//生成 Javadoc 时是否把注解也一起输出
//→ 让 IDE/JavaDoc 把 @OperationLog 显示在方法文档里，方便团队阅读。
@Documented
public @interface OperationLog {

    /**
     * 模块名称
     */
    String module() default "";

    /**
     * 操作类型
     */
    OperationType type() default OperationType.OTHER;

    /**
     * 操作描述
     */
    String description() default "";

    /**
     * 是否保存请求参数
     */
    boolean saveParams() default true;

    /**
     * 是否保存返回结果
     */
    boolean saveResult() default false;

}
