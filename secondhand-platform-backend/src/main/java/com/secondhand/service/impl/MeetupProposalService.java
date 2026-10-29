package com.secondhand.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.secondhand.constant.OrderStatus;
import com.secondhand.entity.MeetupProposal;
import com.secondhand.entity.Order;
import com.secondhand.mapper.MeetupProposalMapper;
import com.secondhand.mapper.OrderMapper;
import com.secondhand.service.MessageService;
import com.secondhand.service.NotificationService;
import com.secondhand.util.Result;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 面交预约业务：记录时间提议，并在买家确认后更新订单时间。
 */
@Service
public class MeetupProposalService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private MeetupProposalMapper meetupProposalMapper;
    @Autowired
    private MessageService messageService;
    @Autowired
    private NotificationService notificationService;

    /** 锁定订单行，让同一订单的预约和状态变更按顺序处理。 */
    private Order lockOrder(Long orderId) {
        return orderMapper.selectOne(new QueryWrapper<Order>()
                .eq("id", orderId)
                .last("FOR UPDATE"));
    }

    /** 卖家为待面交订单提出新的时间。 */
    @Transactional
    public Result propose(Long userId, Long orderId, Date proposedTime) {
        // 1. 校验面交时间，必须选择未来的时间
        if (proposedTime == null || !proposedTime.after(new Date())) {
            return Result.error("请选择未来的面交时间");
        }

        // 2. 锁定订单，校验卖家身份和订单状态
        Order order = lockOrder(orderId);
        if (order == null || !order.getSellerId().equals(userId)) {
            return Result.error("仅订单卖家可提议时间");
        }
        if (!OrderStatus.isWaitMeetup(order.getStatus())) {
            return Result.error("仅待面交订单可以预约");
        }

        // 3. 将旧的待确认提议设为失效，保留已经约定的面交时间
        meetupProposalMapper.update(null, new UpdateWrapper<MeetupProposal>()
                .eq("order_id", orderId)
                .eq("status", "pending")
                .set("status", "expired"));

        // 4. 保存新的时间提议和订单快照
        MeetupProposal proposal = new MeetupProposal();
        proposal.setOrderId(orderId); // 关联订单ID
        proposal.setSellerId(userId);
        proposal.setBuyerId(order.getBuyerId());
        // 卡片沿用订单快照，避免商品修改影响已约定的交易信息。
        proposal.setOrderNo(order.getOrderNo());
        proposal.setProductTitle(order.getSnapshotTitle());
        proposal.setAddress(order.getAddress());
        proposal.setProposedTime(proposedTime);
        proposal.setStatus("pending"); // 等待买家确认
        proposal.setCreateTime(new Date());
        meetupProposalMapper.insert(proposal);

        // 5. 补充订单状态，发送预约卡片并通知买家
        // 前端根据订单状态判断卡片能否操作。
        proposal.setOrderStatus(order.getStatus());
        proposal.setCurrentMeetupTime(order.getMeetupTime());
        messageService.sendMeetupProposal(proposal, order.getProductId());
        notificationService.notifyUser(order.getBuyerId(), "order", "待确认面交时间",
                "卖家提出了新的面交时间，请到聊天中确认", orderId);

        // 6. 返回预约信息
        return Result.success(proposal);
    }

    /** 买家确认或拒绝时间；只有确认时才修改订单的面交时间。 */
    @Transactional
    public Result respond(Long userId, Long proposalId, boolean accept) {
        // 1. 查询提议，校验当前用户是否为买家
        MeetupProposal existingProposal = meetupProposalMapper.selectById(proposalId);
        if (existingProposal == null || !existingProposal.getBuyerId().equals(userId)) {
            return Result.error("仅订单买家可以确认或拒绝");
        }

        // 2. 锁定订单和提议，校验提议是否仍然有效
        Order order = lockOrder(existingProposal.getOrderId());
        // 在订单锁内重新读取，避免确认已被替换的提议。
        MeetupProposal proposal = meetupProposalMapper.selectOne(new QueryWrapper<MeetupProposal>()
                .eq("id", proposalId)
                .last("FOR UPDATE"));
        if (order == null || proposal == null || !order.getBuyerId().equals(userId)) {
            return Result.error("订单不存在或无权操作");
        }
        proposal.setOrderStatus(order.getStatus());
        if (!"pending".equals(proposal.getEffectiveStatus())) {
            return Result.error("提议已处理或失效，请刷新聊天");
        }

        // 3. 买家同意时，更新订单的面交时间
        if (accept) {
            int changed = orderMapper.update(null, new UpdateWrapper<Order>()
                    .eq("id", order.getId())
                    .eq("status", OrderStatus.WAIT_MEETUP)
                    .set("meetup_time", proposal.getProposedTime()));
            if (changed != 1) {
                return Result.error("订单状态已变化，请刷新");
            }
            order.setMeetupTime(proposal.getProposedTime());
        }

        // 4. 保存处理结果和处理时间
        // 拒绝只修改提议状态，不覆盖之前约定的面交时间。
        proposal.setStatus(accept ? "confirmed" : "declined");
        proposal.setRespondedTime(new Date());
        meetupProposalMapper.updateById(proposal);
        proposal.setCurrentMeetupTime(order.getMeetupTime());

        // 5. 通知卖家处理结果
        notificationService.notifyUser(order.getSellerId(), "order", accept ? "面交时间已确认" : "面交时间未同意",
                accept ? "买家已确认面交时间，请按约面交" : "买家认为时间不合适，请继续私信协商后重新提议", order.getId());

        // 6. 返回处理后的预约信息
        return Result.success(proposal);
    }

    /** 仅订单买卖双方可以查看预约卡片。 */
    public Result get(Long userId, Long proposalId) {
        // 1. 查询提议，校验当前用户是否为订单买卖双方
        MeetupProposal proposal = meetupProposalMapper.selectById(proposalId);
        if (proposal == null || userId == null
                || (!userId.equals(proposal.getBuyerId()) && !userId.equals(proposal.getSellerId()))) {
            return Result.error("提议不存在或无权查看");
        }

        // 2. 补充最新订单状态和已确认的面交时间
        Order order = orderMapper.selectById(proposal.getOrderId());
        proposal.setOrderStatus(order == null ? null : order.getStatus());
        proposal.setCurrentMeetupTime(order == null ? null : order.getMeetupTime());

        // 3. 返回预约信息
        return Result.success(proposal);
    }
}
