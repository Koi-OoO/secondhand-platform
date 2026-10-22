package com.secondhand.controller;

import com.secondhand.service.SessionAccountService;
import com.secondhand.util.Result;
import javax.servlet.http.HttpServletRequest;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user/accounts")
public class SessionAccountController {
    private final SessionAccountService accounts;

    public SessionAccountController(SessionAccountService accounts) {
        this.accounts = accounts;
    }

    @Data
    public static class Credentials {
        private String username;
        private String password;
    }

    @Data
    public static class Target {
        private Long userId;
    }

    @GetMapping
    public Result state(HttpServletRequest request) {
        return Result.success(accounts.state(request));
    }

    /** 验证一次密码并加入会话，保持当前账号。 */
    @PostMapping("/add")
    public Result add(HttpServletRequest request, @RequestBody Credentials body) {
        return Result.success(accounts.add(request, body.getUsername(), body.getPassword()));
    }

    @PostMapping("/switch")
    public Result switchAccount(HttpServletRequest request, @RequestBody Target body) {
        return Result.success(accounts.switchAccount(request, body.getUserId()));
    }

    @PostMapping("/remove")
    public Result remove(HttpServletRequest request, @RequestBody Target body) {
        return Result.success(accounts.remove(request, body.getUserId()));
    }

    @PostMapping("/logout-all")
    public Result logoutAll(HttpServletRequest request) {
        return Result.success(accounts.logoutAll(request));
    }
}
