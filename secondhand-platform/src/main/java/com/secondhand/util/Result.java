package com.secondhand.util;

import com.secondhand.entity.Order;
import com.secondhand.entity.Product;
import com.secondhand.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 统一接口返回结构
 */
@Data  // Lombok注解，自动生成getter/setter/toString等方法
@Schema(description = "统一响应结构")  // Swagger文档说明
public class Result {

    @Schema(description = "业务状态码", example = "200", allowableValues = {"200", "500"})  // 状态码字段的Swagger说明
    private Integer code;  // 业务状态码：200成功，500失败

    @Schema(description = "提示信息", example = "成功")  // 消息字段的Swagger说明
    private String message;  // 提示信息

    @Schema(description = "响应数据", anyOf = {Product.class, Order.class, User.class})  // 数据字段的Swagger说明
    private Object data;  // 响应数据，可以是任意类型

    /**
     * 构造成功响应
     */
    public static Result success(Object data) {
        Result result = new Result();  // 创建Result对象
        result.setCode(200);  // 设置状态码为200（成功）
        result.setMessage("成功");  // 设置成功消息
        result.setData(data);  // 设置响应数据
        return result;  // 返回结果对象
    }

    /**
     * 构造失败响应
     */
    public static Result error(String message) {
        Result result = new Result();  // 创建Result对象
        result.setCode(500);  // 设置状态码为500（失败）
        result.setMessage(message);  // 设置错误消息
        return result;  // 返回结果对象
    }
}