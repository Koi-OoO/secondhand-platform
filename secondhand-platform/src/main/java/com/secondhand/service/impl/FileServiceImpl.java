package com.secondhand.service.impl;

import com.secondhand.service.FileService;
import com.secondhand.util.FileUploadUtil;
import com.secondhand.util.Result;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件服务实现类。
 * 这里只负责转发上传请求，真正的校验和存储逻辑放在 {@link FileUploadUtil} 中。
 */
@Service
public class FileServiceImpl implements FileService {

    @Autowired
    private FileUploadUtil fileUploadUtil;

    /**
     * 上传单张图片。
     */
    @Override
    public Result uploadImage(MultipartFile file) {
        // 单图上传直接复用底层工具类
        return fileUploadUtil.upload(file);
    }

    /**
     * 批量上传图片。
     */
    @Override
    public Result uploadBatch(List<MultipartFile> files) {
        // 批量上传同样交给工具类统一处理
        return fileUploadUtil.uploadBatch(files);
    }
}
