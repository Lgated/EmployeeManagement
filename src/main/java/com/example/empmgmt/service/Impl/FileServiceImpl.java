package com.example.empmgmt.service.Impl;

import com.example.empmgmt.common.Exception.BusinessException;
import com.example.empmgmt.service.FileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class FileServiceImpl implements FileService {

    @Value("${file.upload-path}")
    private String uploadPath;

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(".jpg", ".jpeg", ".png", ".gif");
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    @Override
    public String uploadFile(MultipartFile file, String subPath) {
        // 1、验证文件
        if (file.isEmpty()){
            throw new BusinessException("文件不呢个为空");
        }

        // 验证文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("文件大小不能超过 5MB");
        }


        // 2、生成唯一文件名
        String originalFilename = file.getOriginalFilename();
        if(originalFilename == null || originalFilename.isEmpty()){
            throw new BusinessException("文件名无效");
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("只支持上传 jpg、png、gif 格式的图片");
        }
        String newFilename = UUID.randomUUID().toString() + extension;

        // 3、创建目录
        String normalizedSubPath = subPath.startsWith("/") ? subPath.substring(1) : subPath;
        File destDir = new File(uploadPath, normalizedSubPath);  // 使用 File(File, String) 构造器更安全
        if (!destDir.exists()) {
            destDir.mkdirs();
        }

        // 4、保存文件
        File destFile = new File(destDir, newFilename);
        try {
            file.transferTo(destFile);
        } catch (IOException e) {
            throw new BusinessException("文件保存失败: " + e.getMessage());
        }

        // 5、返回访问URL（统一格式：/uploads/avatars/xxx.png）
        return "/uploads/" + normalizedSubPath + "/" + newFilename;
    }
}
