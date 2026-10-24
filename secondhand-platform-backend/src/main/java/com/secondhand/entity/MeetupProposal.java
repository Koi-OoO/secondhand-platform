package com.secondhand.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

/** 每次面交时间提议独立保存，便于查看协商历史。 */
@Data
@TableName("meetup_proposal")
public class MeetupProposal {
    @TableId(type = IdType.AUTO) private Long id;
    private Long orderId;
    private Long sellerId;
    private Long buyerId;
    private String orderNo;
    private String productTitle;
    private String address;
    private Date proposedTime;
    private String status;
    private Date createTime;
    private Date respondedTime;
    @TableField(exist = false) private Integer orderStatus;
    @TableField(exist = false) private Date currentMeetupTime;

    public String getEffectiveStatus() {
        if ("pending".equals(status) && (!Integer.valueOf(1).equals(orderStatus)
                || proposedTime == null || !proposedTime.after(new Date()))) return "expired";
        return status;
    }
}
