package com.example.empmgmt.repository;


import com.example.empmgmt.domain.OperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {

    /**
     * 根据用户ID分页查询
     */
    Page<OperationLog> findByUserId(Long userId, Pageable pageable);

    /**
     * 根据模块分页查询
     */
    Page<OperationLog> findByModule(String module, Pageable pageable);

    /**
     * 根据操作类型分页查询
     */
    Page<OperationLog> findByOperationType(String operationType, Pageable pageable);
}
