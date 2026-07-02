package com.secondhand.controller;

import com.secondhand.entity.Evaluation;
import com.secondhand.service.EvaluationService;
import com.secondhand.util.Result;
import com.secondhand.util.SessionUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评价模块控制器
 */
@RestController  // 标识为REST控制器，返回JSON数据
@RequestMapping("/evaluation")  // 请求路径前缀为 /evaluation
@Tag(name = "评价管理", description = "提供订单评价提交和公开评价查询接口")  // Swagger文档标签
public class EvaluationController {

    @Autowired  // 自动注入EvaluationService
    private EvaluationService evaluationService;

    /**
     * 提交订单评价
     */
    @PostMapping  // 处理POST请求，路径为 /evaluation
    @Operation(summary = "提交评价", description = "需要登录，买家可为已完成订单提交评分、评价内容和匿名标记")
    @ApiResponse(responseCode = "200", description = "统一Result响应")
    public Result evaluate(@RequestParam Long orderId,  // 订单ID，从请求参数获取
                           @RequestParam Integer rating,  // 评分（1-5星）
                           @RequestParam(required = false) String content,  // 评价内容，可选
                           @RequestParam(defaultValue = "false") Boolean anonymous,  // 是否匿名，默认false
                           HttpSession session) {  // HttpSession对象，用于获取当前用户
        // 从session中获取用户ID
        Long userId = SessionUtil.getUserId(session);
        // 未登录
        if (userId == null) {
            return Result.error("未登录");
        }
        // 调用service层提交评价
        return evaluationService.evaluate(userId, orderId, rating, content, anonymous);
    }

    /**
     * 查询用户收到的评价
     */
    @GetMapping("/user/{userId}")  // 处理GET请求，路径为 /evaluation/user/{userId}
    @Operation(summary = "查询收到的评价", description = "公开接口，返回指定用户收到的评价列表，匿名评价会隐藏评价人身份")
    @ApiResponse(responseCode = "200", description = "Result.data.records中包含评价列表",
            content = @Content(schema = @Schema(implementation = Evaluation.class)))
    public Result userEvals(@PathVariable Long userId,  // 用户ID，从路径中获取
                            @RequestParam(defaultValue = "1") Integer page,  // 页码，默认1
                            @RequestParam(defaultValue = "12") Integer size) {  // 每页条数，默认12
        // 调用service层分页查询用户收到的评价
        return evaluationService.getUserEvaluations(userId, page, size);
    }
}