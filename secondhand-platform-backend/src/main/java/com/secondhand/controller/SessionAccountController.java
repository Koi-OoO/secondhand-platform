package com.secondhand.controller;

import com.secondhand.service.SessionAccountService;
import com.secondhand.util.Result;
import javax.servlet.http.HttpServletRequest;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 会话内的账号管理接口，授权校验由业务层统一处理。 */
@RestController
@RequestMapping("/user/accounts")
public class SessionAccountController {
    private final SessionAccountService sessionAccountService;

    public SessionAccountController(SessionAccountService sessionAccountService) {
        this.sessionAccountService = sessionAccountService;
    }

    @Data
    public static class Credentials {
        private String username; // 待添加的账号
        private String password; // 本次验证使用，不保存到账号列表
    }

    @Data
    public static class Target {
        private Long userId; // 待切换或移除的已授权账号ID
    }

    @GetMapping
    public Result state(HttpServletRequest request) {
        // 读取本次会话内的有效账号，管理权限由业务层校验。
        return Result.success(sessionAccountService.state(request));
    }

    /** 验证一次密码并加入会话，保持当前账号。 */
    @PostMapping("/add")
    public Result add(HttpServletRequest request, @RequestBody Credentials body) {
        // 验证密码后添加授权，保持当前身份。
        return Result.success(sessionAccountService.add(request, body.getUsername(), body.getPassword()));
    }

    @PostMapping("/switch")
    public Result switchAccount(HttpServletRequest request, @RequestBody Target body) {
        // 校验目标授权后切换当前账号。
        return Result.success(sessionAccountService.switchAccount(request, body.getUserId()));
    }

    @PostMapping("/remove")
    public Result remove(HttpServletRequest request, @RequestBody Target body) {
        // 只移除会话授权，不删除数据库账号。
        return Result.success(sessionAccountService.remove(request, body.getUserId()));
    }

    @PostMapping("/logout-all")
    public Result logoutAll(HttpServletRequest request) {
        // 撤销全部会话授权并关闭对应连接。
        return Result.success(sessionAccountService.logoutAll(request));
    }
}
