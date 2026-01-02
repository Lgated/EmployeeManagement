package com.example.empmgmt.controller;


import com.example.empmgmt.domain.OperationLog;
import com.example.empmgmt.dto.response.PageResponse;
import com.example.empmgmt.dto.response.Result;
import com.example.empmgmt.repository.OperationLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/logs")
public class OperationLogController {

    private final OperationLogRepository logRepository;

    public OperationLogController(OperationLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    @GetMapping
    public Result<PageResponse<OperationLog>> list(
            @RequestParam(required = false)String module,
            @RequestParam(required = false)String operationType,
            @RequestParam(defaultValue = "1")int page,
            @RequestParam(defaultValue = "10")int size
    ){
        PageRequest pageable = PageRequest.of(
                page - 1,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<OperationLog> logPage;
        // pageRequest实现了pageable接口(父类AbstractPageRequest)
        if (module != null && !module.isBlank()) {
            logPage = logRepository.findByModule(module, pageable);
        } else if (operationType != null && !operationType.isBlank()) {
            logPage = logRepository.findByOperationType(operationType, pageable);
        } else {
            logPage = logRepository.findAll(pageable);
        }

        return Result.success(PageResponse.of(logPage));
    }

}
