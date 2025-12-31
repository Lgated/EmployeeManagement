package com.example.empmgmt.controller;

import com.example.empmgmt.dto.response.Result;
import com.example.empmgmt.service.FileService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService){
        this.fileService = fileService;
    }

    /**
     * 上传文件（头像）
     */
    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file")MultipartFile multipartFile){
        // 将头像上传到 avatars 目录下
        String url = fileService.uploadFile(multipartFile, "avatars");
        return Result.success(url);
    }
}
