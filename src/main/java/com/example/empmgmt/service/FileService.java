package com.example.empmgmt.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    /**
     * 上传文件
     */
    String uploadFile(MultipartFile file,String subPath);



}
