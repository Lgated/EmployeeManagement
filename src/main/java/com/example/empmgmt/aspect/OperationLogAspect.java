package com.example.empmgmt.aspect;

import com.example.empmgmt.domain.OperationLog;
import com.example.empmgmt.repository.OperationLogRepository;
import com.example.empmgmt.util.SecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 操作日志切面
 * 拦截所有带有 @OperationLog 注解的方法，自动记录操作日志
 */
@Aspect
@Component
@Slf4j
public class OperationLogAspect {

    private final OperationLogRepository operationLogRepository;
    private final ObjectMapper objectMapper;

    public OperationLogAspect(OperationLogRepository operationLogRepository,
                              ObjectMapper objectMapper) {
        this.operationLogRepository = operationLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 环绕通知：在方法执行前后记录日志
     */
    @Around("@annotation(com.example.empmgmt.annotation.OperationLog)")
    public Object around(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        // 获取注解信息
        MethodSignature signature = (MethodSignature) proceedingJoinPoint.getSignature();
        Method method = signature.getMethod();
        com.example.empmgmt.annotation.OperationLog annotation =
                method.getAnnotation(com.example.empmgmt.annotation.OperationLog.class);

        // 创建日志对象
        OperationLog operationLog = new OperationLog();

        // 1. 获取当前用户信息
        try {
            Long userId = SecurityUtil.getCurrentUserId();
            String username = SecurityUtil.getCurrentUsername();
            operationLog.setUserId(userId);
            operationLog.setUsername(username);
        } catch (Exception e) {
            operationLog.setUserId(0L);
            operationLog.setUsername("anonymous");
        }

        // 2. 设置操作信息
        operationLog.setOperationType(annotation.type().name());
        operationLog.setModule(annotation.module());
        operationLog.setDescription(annotation.description());
        operationLog.setMethod(method.getDeclaringClass().getName() + "." + method.getName());

        // 3. 获取IP地址
        HttpServletRequest request = getHttpServletRequest();
        if (request != null) {
            operationLog.setIpAddress(getIpAddress(request));
        }

        // 4. 保存请求参数
        if (annotation.saveParams()) {
            try {
                Object[] args = proceedingJoinPoint.getArgs();
                operationLog.setParams(objectMapper.writeValueAsString(args));
            } catch (Exception e) {
                operationLog.setParams("参数序列化失败: " + e.getMessage());
            }
        }

        // 5. 执行目标方法并记录结果
        Object result = null;
        try {
            result = proceedingJoinPoint.proceed();
            operationLog.setStatus("SUCCESS");

            // 保存返回结果
            if (annotation.saveResult() && result != null) {
                try {
                    operationLog.setResult(objectMapper.writeValueAsString(result));
                } catch (Exception e) {
                    operationLog.setResult("结果序列化失败: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            operationLog.setStatus("FAILURE");
            operationLog.setErrorMessage(e.getMessage());
            throw e;
        } finally {
            // 6. 计算执行耗时
            long endTime = System.currentTimeMillis();
            operationLog.setExecutionTime(endTime - startTime);
            operationLog.setCreatedAt(LocalDateTime.now());
            
            // 7. 异步保存日志（避免影响业务性能）
            saveLogAsync(operationLog);
        }

        return result;
    }
    /**
     * 异步保存日志
     */
    private void saveLogAsync(OperationLog operationLog) {
        try {
            operationLogRepository.save(operationLog);
        } catch (Exception e) {
            log.error("保存操作日志失败", e);
        }
    }

    /**
     * 获取 HttpServletRequest
     */
    private HttpServletRequest getHttpServletRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    /**
     * 获取真实IP地址
     */
    private String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
