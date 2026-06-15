package com.secondhand.service;

import com.secondhand.util.Result;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传服务接口
 * 定义商品图片上传相关能力，
 * 对外统一暴露单图上传和批量上传方法。
 */
public interface FileService {

    /**
     * 上传单张图片
     *
     * @param file 待上传文件
     * @return Result 上传结果
     */
    Result uploadImage(MultipartFile file);

    /**
     * 批量上传图片
     *
     * @param files 待上传文件列表
     * @return Result 批量上传结果
     */
    Result uploadBatch(List<MultipartFile> files);
}
