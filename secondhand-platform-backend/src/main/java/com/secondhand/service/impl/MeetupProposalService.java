package com.secondhand.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.secondhand.entity.*;
import com.secondhand.mapper.*;
import com.secondhand.service.*;
import com.secondhand.util.Result;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeetupProposalService {
    @Autowired private OrderMapper orders;
    @Autowired private MeetupProposalMapper proposals;
    @Autowired private MessageService messages;
    @Autowired private NotificationService notifications;

    private Order lockOrder(Long id) {
        return orders.selectOne(new QueryWrapper<Order>().eq("id", id).last("FOR UPDATE"));
    }
    @Transactional
    public Result propose(Long userId, Long orderId, Date time) {
        if (time == null || !time.after(new Date())) return Result.error("请选择未来的面交时间");
        Order order = lockOrder(orderId);
        if (order == null || !order.getSellerId().equals(userId)) return Result.error("仅订单卖家可提议时间");
        if (!Integer.valueOf(1).equals(order.getStatus())) return Result.error("仅待面交订单可以预约");
        proposals.update(null, new UpdateWrapper<MeetupProposal>().eq("order_id", orderId)
                .eq("status", "pending").set("status", "expired"));
        MeetupProposal p = new MeetupProposal();
        p.setOrderId(orderId); p.setSellerId(userId); p.setBuyerId(order.getBuyerId());
        p.setOrderNo(order.getOrderNo()); p.setProductTitle(order.getSnapshotTitle()); p.setAddress(order.getAddress());
        p.setProposedTime(time); p.setStatus("pending"); p.setCreateTime(new Date());
        proposals.insert(p);
        p.setOrderStatus(order.getStatus()); p.setCurrentMeetupTime(order.getMeetupTime());
        messages.sendMeetupProposal(p, order.getProductId());
        notifications.notifyUser(order.getBuyerId(), "order", "待确认面交时间", "卖家提出了新的面交时间，请到聊天中确认", orderId);
        return Result.success(p);
    }
    @Transactional
    public Result respond(Long userId, Long proposalId, boolean accept) {
        MeetupProposal first = proposals.selectById(proposalId);
        if (first == null || !first.getBuyerId().equals(userId)) return Result.error("仅订单买家可以确认或拒绝");
        Order order = lockOrder(first.getOrderId());
        // 在订单锁内重新读取，避免确认已被替换的提议。
        MeetupProposal p = proposals.selectOne(new QueryWrapper<MeetupProposal>().eq("id", proposalId).last("FOR UPDATE"));
        if (order == null || p == null || !order.getBuyerId().equals(userId)) return Result.error("订单不存在或无权操作");
        p.setOrderStatus(order.getStatus());
        if (!"pending".equals(p.getEffectiveStatus())) return Result.error("提议已处理或失效，请刷新聊天");
        if (accept) {
            int changed = orders.update(null, new UpdateWrapper<Order>().eq("id", order.getId()).eq("status", 1)
                    .set("meetup_time", p.getProposedTime()));
            if (changed != 1) return Result.error("订单状态已变化，请刷新");
            order.setMeetupTime(p.getProposedTime());
        }
        p.setStatus(accept ? "confirmed" : "declined"); p.setRespondedTime(new Date());
        proposals.updateById(p);
        p.setCurrentMeetupTime(order.getMeetupTime());
        notifications.notifyUser(order.getSellerId(), "order", accept ? "面交时间已确认" : "面交时间未同意",
                accept ? "买家已确认面交时间，请按约面交" : "买家认为时间不合适，请继续私信协商后重新提议", order.getId());
        return Result.success(p);
    }
    public Result get(Long userId, Long id) {
        MeetupProposal p = proposals.selectById(id);
        if (p == null || userId == null || (!userId.equals(p.getBuyerId()) && !userId.equals(p.getSellerId()))) return Result.error("提议不存在或无权查看");
        Order order = orders.selectById(p.getOrderId());
        p.setOrderStatus(order == null ? null : order.getStatus());
        p.setCurrentMeetupTime(order == null ? null : order.getMeetupTime());
        return Result.success(p);
    }
}
