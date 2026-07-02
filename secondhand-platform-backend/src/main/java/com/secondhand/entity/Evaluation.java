package com.secondhand.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import lombok.Data;

/**
 * 订单评价实体。
 */
@Data
@TableName("evaluation")
@Schema(description = "用户评价")
public class Evaluation {

    @TableId(type = IdType.AUTO)
    @Schema(description = "评价 ID", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @Schema(description = "订单 ID")
    private Long orderId;

    @Schema(description = "评价人用户 ID")
    private Long fromUserId;

    @Schema(description = "被评价用户 ID")
    private Long toUserId;

    @Schema(description = "评分，范围 1 到 5")
    private Integer rating;

    @Schema(description = "评价内容")
    private String content;

    @Schema(description = "是否匿名评价")
    private Boolean anonymous;

    @Schema(description = "创建时间", accessMode = Schema.AccessMode.READ_ONLY)
    private Date createTime;

    @TableField(exist = false)
    @Schema(description = "评价人昵称")
    private String fromUserNickname;

    @TableField(exist = false)
    @Schema(description = "评价人头像")
    private String fromUserAvatar;

    @TableField(exist = false)
    @Schema(description = "关联商品标题")
    private String productTitle;

    @TableField(exist = false)
    @Schema(description = "关联商品图片")
    private String productImage;

    @TableField(exist = false)
    @Schema(description = "关联商品 ID")
    private Long productId;
}
