package com.secondhand.controller;
import com.secondhand.service.impl.MeetupProposalService;
import com.secondhand.util.*;
import java.util.Date;
import javax.servlet.http.HttpSession;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/meetup-proposals")
public class MeetupProposalController {
    @Autowired private MeetupProposalService service;
    @Data public static class ProposalRequest { private Long orderId; private Date proposedTime; }
    @Data public static class ResponseRequest { private Boolean accept; }
    @PostMapping public Result propose(@RequestBody ProposalRequest body, HttpSession session) {
        Long id=SessionUtil.getUserId(session);
        if(id==null) return Result.error("未登录");
        if(body.getOrderId()==null) return Result.error("请选择订单");
        return service.propose(id,body.getOrderId(),body.getProposedTime());
    }
    @PutMapping("/{id}/response") public Result respond(@PathVariable Long id,@RequestBody ResponseRequest body,HttpSession session) {
        Long user=SessionUtil.getUserId(session);
        if(user==null) return Result.error("未登录");
        if(body.getAccept()==null) return Result.error("请选择确认或时间不合适");
        return service.respond(user,id,body.getAccept());
    }
    @GetMapping("/{id}") public Result get(@PathVariable Long id,HttpSession session) {
        Long user=SessionUtil.getUserId(session);
        if(user==null) return Result.error("未登录");
        return service.get(user,id);
    }
}
