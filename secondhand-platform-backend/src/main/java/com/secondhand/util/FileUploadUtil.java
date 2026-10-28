package com.secondhand.util;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.PutObjectRequest;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传工具类（支持OSS云存储和本地存储两种模式）
 */
public class FileUploadUtil {

    // 日志记录器
    private static final Logger log = LoggerFactory.getLogger(FileUploadUtil.class);

    // 允许上传的图片格式
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList("jpg", "jpeg", "png", "webp"));
    // 单个文件最大5MB
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    // 单次最多上传9张图片
    private static final int MAX_FILE_COUNT = 9;
    // OSS存储路径前缀
    private static final String OSS_PATH_PREFIX = "images/";

    // OSS配置参数
    private final String endpoint;           // OSS访问域名
    private final String accessKeyId;        // OSS访问密钥ID
    private final String accessKeySecret;    // OSS访问密钥
    private final String bucketName;         // OSS存储桶名称
    private final String localStoragePath;   // 本地存储根目录

    private OSS ossClient;  // OSS客户端

    /**
     * 构造器（支持OSS与本地双模式）
     */
    public FileUploadUtil(String endpoint, String accessKeyId, String accessKeySecret,
                          String bucketName, String localStoragePath) {
        this.endpoint = endpoint;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.bucketName = bucketName;
        this.localStoragePath = localStoragePath;
    }

    /**
     * 构造器（仅本地存储模式）
     */
    public FileUploadUtil(String localStoragePath) {
        this(null, null, null, null, localStoragePath);
    }

    /**
     * 初始化上传客户端（Spring容器启动时调用）
     */
    public void init() {
        // 配置了OSS地址时才创建客户端，否则沿用本地存储
        if (endpoint != null && !endpoint.isEmpty()) {
            ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
            log.info("OSS客户端初始化成功：存储桶={} 访问地址={}", bucketName, endpoint);
        }
    }

    /**
     * 销毁上传客户端（Spring容器关闭时调用）
     */
    public void destroy() {
        // 关闭OSS客户端，释放连接资源
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }

    /**
     * 上传单个文件（优先使用OSS，若无则用本地）
     */
    public Result upload(MultipartFile file) {
        return upload(file, true);
    }

    /**
     * 强制使用本地上传单个文件
     */
    public Result uploadLocal(MultipartFile file) {
        return upload(file, false);
    }

    /**
     * 单文件上传核心逻辑
     */
    private Result upload(MultipartFile file, boolean useOssStorage) {
        // 1. 校验文件是否为空、大小和格式是否合法
        Result validateError = validate(file);
        if (validateError != null) {
            return validateError;
        }

        // 2. 获取原文件名和扩展名
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();

        // 3. 生成唯一文件名（UUID + 扩展名）
        String objectName = OSS_PATH_PREFIX + UUID.randomUUID().toString().replace("-", "") + "." + extension;

        // 4. 按配置选择OSS或本地存储
        String url;
        if (useOssStorage && ossClient != null) {
            url = uploadToOss(file, bucketName, objectName);
            if (url == null) {
                return Result.error("文件上传失败，请重试");
            }
        } else {
            url = uploadToLocal(file, objectName);
            if (url == null) {
                return Result.error("文件保存失败，请重试");
            }
        }

        log.info("文件上传成功：{} -> {}", originalFilename, url);
        return Result.success(url);
    }

    /**
     * 批量上传（优先使用OSS）
     */
    public Result uploadBatch(List<MultipartFile> files) {
        return uploadBatch(files, true);
    }

    /**
     * 批量上传（强制本地）
     */
    public Result uploadBatchLocal(List<MultipartFile> files) {
        return uploadBatch(files, false);
    }

    /**
     * 批量上传核心逻辑
     */
    private Result uploadBatch(List<MultipartFile> files, boolean useOssStorage) {
        // 1. 校验文件列表非空且不超过数量上限
        if (files == null || files.isEmpty()) {
            return Result.error("请选择要上传的文件");
        }
        if (files.size() > MAX_FILE_COUNT) {
            return Result.error("最多批量上传 " + MAX_FILE_COUNT + " 张图片");
        }

        // 2. 逐个上传；任意一张失败就立即返回失败结果
        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files) {
            Result uploadResult = upload(file, useOssStorage);
            if (uploadResult.getCode() == 200) {
                urls.add(uploadResult.getData().toString());
            } else {
                return uploadResult;
            }
        }
        return Result.success(urls);
    }

    /**
     * 校验上传文件
     */
    private Result validate(MultipartFile file) {
        // 1. 文件不能为空
        if (file == null || file.isEmpty()) {
            return Result.error("请选择要上传的文件");
        }

        // 2. 文件大小不能超过5MB
        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.error("文件大小不能超过 5MB");
        }

        // 3. 文件名必须包含扩展名
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            return Result.error("文件格式不支持");
        }

        // 4. 扩展名必须在允许的图片格式内
        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return Result.error("仅支持 jpg、jpeg、png、webp 格式的图片");
        }
        return null;  // 校验通过
    }

    /**
     * 上传文件到OSS
     */
    private String uploadToOss(MultipartFile file, String bucket, String objectName) {
        try {
            // 1. 创建并执行OSS上传请求
            PutObjectRequest request = new PutObjectRequest(bucket, objectName, file.getInputStream());
            ossClient.putObject(request);

            // 2. 设置公共读权限，方便前端直接访问
            ossClient.setObjectAcl(bucket, objectName, CannedAccessControlList.PublicRead);
        } catch (IOException exception) {
            log.error("OSS上传失败：{}", objectName, exception);
            return null;
        }

        // 3. 返回可访问的URL
        return "https://" + bucketName + "." + endpoint + "/" + objectName;
    }

    /**
     * 上传文件到本地磁盘
     */
    private String uploadToLocal(MultipartFile file, String objectName) {
        try {
            // 1. 确保本地图片目录存在
            File dir = new File(localStoragePath + OSS_PATH_PREFIX);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // 2. 去掉OSS前缀后保存到本地目录
            File dest = new File(dir, objectName.substring(OSS_PATH_PREFIX.length()));
            file.transferTo(dest);
        } catch (IOException exception) {
            log.error("本地文件保存失败：{}", objectName, exception);
            return null;
        }

        // 3. 返回通过静态资源映射访问的URL
        return "/uploads/" + objectName;
    }
}
