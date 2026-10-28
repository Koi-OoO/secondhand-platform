package com.secondhand.controller;

import com.secondhand.service.impl.MeetupProposalService;
import com.secondhand.util.Result;
import com.secondhand.util.SessionUtil;
import java.util.Date;
import javax.servlet.http.HttpSession;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面交时间预约接口：卖家提议，买家确认或拒绝。
 */
@RestController
@RequestMapping("/meetup-proposals")
public class MeetupProposalController {
    @Autowired
    private MeetupProposalService meetupProposalService;

    /** 卖家发起预约时提交的数据。 */
    @Data
    public static class ProposalRequest {
        private Long orderId;       // 订单ID
        private Date proposedTime; // 提议的面交时间
    }

    /** 买家对预约的处理结果。 */
    @Data
    public static class ResponseRequest {
        private Boolean accept; // true：确认时间；false：时间不合适
    }

    @PostMapping
    public Result propose(@RequestBody ProposalRequest request, HttpSession session) {
        // 1. 获取当前用户，校验登录状态
        Long userId = SessionUtil.getUserId(session);
        if (userId == null) {
            return Result.error("未登录");
        }

        // 2. 校验是否选择订单
        if (request.getOrderId() == null) {
            return Result.error("请选择订单");
        }

        // 3. 调用业务层，发起面交时间提议
        return meetupProposalService.propose(userId, request.getOrderId(), request.getProposedTime());
    }

    @PutMapping("/{id}/response")
    public Result respond(@PathVariable Long id, @RequestBody ResponseRequest request, HttpSession session) {
        // 1. 获取当前用户，校验登录状态
        Long userId = SessionUtil.getUserId(session);
        if (userId == null) {
            return Result.error("未登录");
        }

        // 2. 校验是否选择处理结果
        if (request.getAccept() == null) {
            return Result.error("请选择确认或时间不合适");
        }

        // 3. 调用业务层，确认或拒绝面交时间
        return meetupProposalService.respond(userId, id, request.getAccept());
    }

    @GetMapping("/{id}")
    public Result get(@PathVariable Long id, HttpSession session) {
        // 1. 获取当前用户，校验登录状态
        Long userId = SessionUtil.getUserId(session);
        if (userId == null) {
            return Result.error("未登录");
        }

        // 2. 调用业务层，查询本人有权查看的预约信息
        return meetupProposalService.get(userId, id);
    }
}
