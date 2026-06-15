package com.secondhand.handler;

import com.secondhand.util.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理器（统一处理Controller层抛出的异常）
 */
@RestControllerAdvice  // 标识为REST控制器增强，全局拦截异常并返回JSON格式
public class GlobalExceptionHandler {

    // 日志记录器
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理参数类型不匹配异常（如：需要数字却传了字母）
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)  // 指定处理的异常类型
    public Result handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        // 返回参数格式错误的提示，包含参数名
        return Result.error("参数格式不正确：" + ex.getName());
    }

    /**
     * 处理缺少必填参数异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)  // 指定处理的异常类型
    public Result handleMissingParam(MissingServletRequestParameterException ex) {
        // 返回缺少参数的提示，包含参数名
        return Result.error("缺少必填参数：" + ex.getParameterName());
    }

    /**
     * 处理请求体格式错误异常（如：JSON格式错误）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)  // 指定处理的异常类型
    public Result handleNotReadable(HttpMessageNotReadableException ex) {
        // 返回请求体格式错误的提示
        return Result.error("请求体格式错误");
    }

    /**
     * 兜底异常处理（处理所有未被上面方法捕获的异常）
     */
    @ExceptionHandler(Exception.class)  // 处理所有Exception类型
    public Result handleException(Exception ex) {
        // 记录错误日志（包含堆栈信息，方便排查）
        log.error("未捕获异常", ex);
        // 返回通用错误提示，不暴露具体错误信息给客户端
        return Result.error("服务器繁忙，请稍后再试");
    }
}