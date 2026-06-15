package com.secondhand.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密码加密与校验工具类
 */
public class PasswordUtil {

    // 盐值长度：16字节
    private static final int SALT_LEN = 16;

    /**
     * 加密密码，返回"盐值:哈希值"
     * @param password 原始密码
     * @return 加密后的密码串
     */
    public static String encrypt(String password) {
        // 创建16字节的字节数组，用于存放盐值
        byte[] salt = new byte[SALT_LEN];
        // 使用SecureRandom生成安全的随机盐值
        new SecureRandom().nextBytes(salt);
        // 将盐值进行Base64编码，方便存储和拼接
        String saltBase64 = Base64.getEncoder().encodeToString(salt);
        // 返回格式：盐值 + 冒号 + (密码+盐值)的SHA-256哈希值
        return saltBase64 + ":" + sha256(password + saltBase64);
    }

    /**
     * 验证密码是否正确
     * @param input 用户输入的密码
     * @param stored 数据库中存储的密码串
     * @return true=正确，false=错误
     */
    public static boolean verify(String input, String stored) {
        // 如果存储的密码为空，直接返回false
        if (stored == null) {
            return false;
        }
        // 判断是否为新格式（带盐）：包含冒号
        if (stored.contains(":")) {
            // 用冒号分割，分成盐值和哈希值两部分
            String[] parts = stored.split(":", 2);
            // 取出盐值
            String salt = parts[0];
            // 取出正确的哈希值
            String expectedHash = parts[1];
            // 用用户输入的密码+盐值重新计算哈希，与存储的哈希对比
            return sha256(input + salt).equals(expectedHash);
        }
        // 旧格式（无盐）：直接对比用户输入密码的哈希值
        return sha256(input).equals(stored);
    }

    /**
     * 计算字符串的SHA-256哈希值，返回十六进制字符串
     * @param input 待计算的字符串
     * @return 十六进制哈希值
     */
    private static String sha256(String input) {
        try {
            // 获取SHA-256算法的实例
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            // 将输入字符串转为字节数组，并计算哈希值
            byte[] hashBytes = md.digest(input.getBytes());
            // 用于拼接十六进制字符串
            StringBuilder hexString = new StringBuilder();
            // 遍历每个字节
            for (byte b : hashBytes) {
                // 将字节转为无符号整数（0-255）
                // 再转为十六进制字符串
                String hex = Integer.toHexString(0xff & b);
                // 如果只有1位，前面补0（例如 "a" 变成 "0a"）
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                // 追加到结果中
                hexString.append(hex);
            }
            // 返回完整的十六进制字符串
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256算法不可用（正常情况下不会发生）
            throw new RuntimeException("SHA-256 algorithm is not available", e);
        }
    }
}