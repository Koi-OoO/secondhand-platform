package com.secondhand.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.boot.system.ApplicationHome;

/**
 * 上传路径解析工具类（将相对路径转为绝对路径）
 */
public final class UploadPathResolver {

    // 默认本地存储路径
    private static final String DEFAULT_LOCAL_PATH = "uploads";

    /**
     * 私有构造器（工具类不允许实例化）
     */
    private UploadPathResolver() {
    }

    /**
     * 解析上传目录为绝对路径
     */
    public static Path resolve(String configuredPath) {
        // 使用自动探测的基目录进行解析
        return resolve(configuredPath, detectBaseDir());
    }

    /**
     * 根据指定基目录解析上传路径
     */
    static Path resolve(String configuredPath, Path baseDir) {
        // 如果配置了路径就使用配置的，否则使用默认路径
        String pathValue = hasText(configuredPath) ? configuredPath.trim() : DEFAULT_LOCAL_PATH;
        // 创建Path对象并规范化
        Path path = Paths.get(pathValue).normalize();
        // 如果是绝对路径，直接返回
        if (path.isAbsolute()) {
            return path;
        }

        // 规范化基目录
        Path normalizedBaseDir = (baseDir != null ? baseDir : Paths.get("").toAbsolutePath()).normalize();
        // 基目录 + 相对路径 = 绝对路径
        return normalizedBaseDir.resolve(path).normalize();
    }

    /**
     * 自动探测项目根目录
     */
    private static Path detectBaseDir() {
        // 获取当前类所在的运行目录
        Path homeDir = new ApplicationHome(UploadPathResolver.class).getDir().toPath().toAbsolutePath().normalize();
        // 从当前目录开始向上查找pom.xml文件
        Path current = homeDir;
        while (current != null) {
            // 如果找到pom.xml，说明当前目录是项目根目录
            if (Files.exists(current.resolve("pom.xml"))) {
                return current;
            }
            // 继续向上查找父目录
            current = current.getParent();
        }
        // 没找到pom.xml，返回运行目录
        return homeDir;
    }

    /**
     * 判断字符串是否有有效文本（非空且非空白）
     */
    private static boolean hasText(String value) {
        // 不为null，且去除首尾空格后不为空字符串
        return value != null && !value.trim().isEmpty();
    }
}