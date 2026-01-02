package com.example.empmgmt.common.Exception;

import com.example.empmgmt.dto.response.Result;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

//@RestControllerAdvice 是 Spring 的‘全局接盘侠’，让异常都变成统一 JSON 返回。”
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(EntityNotFoundException.class)
    public Result<Void> handleEntityNotFound(EntityNotFoundException e) {
        return Result.error(404, e.getMessage());
    }


    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        // 业务异常直接返回用户提示，不打印堆栈
        return Result.error(e.getCode(), e.getMessage());
    }
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(500, "系统异常，请稍后重试");
    }

    /**
     * 处理权限拒绝异常
     */
    @ExceptionHandler(PermissionDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handlePermissionDeniedException(PermissionDeniedException e) {
        log.warn("权限拒绝: {}", e.getMessage());
        return Result.error(403, e.getMessage());
    }
}
