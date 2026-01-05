package com.example.empmgmt.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class FileUploadConfig implements WebMvcConfigurer {

    @Value("${file.upload-path}")
    private String uploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 确保路径以斜杠结尾
        String resourcePath = uploadPath.endsWith("/") ? uploadPath : uploadPath + "/";

        // 将 /uploads/** 映射到文件系统路径
        // 例如：/uploads/avatars/xxx.png -> D:/uploads/employee/avatars/xxx.png
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + resourcePath);

        // 支持 /avatars/** 路径（有前导斜杠）
        // 例如：/avatars/xxx.png -> D:/uploads/employee/avatars/xxx.png
        registry.addResourceHandler("/avatars/**")
                .addResourceLocations("file:" + resourcePath + "avatars/");
    }
}
