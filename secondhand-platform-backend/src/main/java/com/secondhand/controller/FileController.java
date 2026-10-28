package com.secondhand.controller;

import com.secondhand.service.FileService;
import com.secondhand.util.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传控制器
 */
@RestController  // 标识为REST控制器，返回JSON数据
@RequestMapping("/file")  // 请求路径前缀为 /file
@Tag(name = "文件上传", description = "提供商品图片上传接口，支持单张和批量上传")  // Swagger文档标签
public class FileController {

    @Autowired  // 自动注入FileService
    private FileService fileService;

    /**
     * 上传单张图片
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)  // POST请求，路径/file/upload，接收multipart/form-data格式
    @Operation(summary = "上传单张图片", description = "需要登录，上传一张商品图片；Result.data 返回图片访问URL")  // Swagger接口说明
    public Result upload(@Parameter(description = "图片文件，表单字段名固定为 file", required = true)
                         @RequestParam("file") MultipartFile file) {  // 接收上传的文件，参数名为file
        // 调用业务层，处理单文件上传
        return fileService.uploadImage(file);
    }

    /**
     * 批量上传图片
     */
    @PostMapping(value = "/upload-batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)  // POST请求，路径/file/upload-batch，接收multipart/form-data格式
    @Operation(summary = "批量上传图片", description = "需要登录，一次最多上传9张商品图片；Result.data 返回图片访问URL列表")  // Swagger接口说明
    public Result uploadBatch(@Parameter(description = "图片文件列表，表单字段名固定为 files，一次最多9张", required = true)
                              @RequestParam("files") List<MultipartFile> files) {  // 接收多个文件，参数名为files
        // 调用业务层，处理批量上传
        return fileService.uploadBatch(files);
    }
}
