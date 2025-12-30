package com.example.empmgmt.dto.response;

import java.util.List;

public record PageResponse<T>(
        List<T> records,
        long total,
        int page,
        int size
) {
    public static <T> PageResponse<T> of(org.springframework.data.domain.Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getTotalElements(),
                page.getNumber() + 1,   // Page 从 0 开始，这里转换为 1 开始
                page.getSize()
        );
    }
}
