package com.secondhand.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/**
 * 订单实体。
 */
@Data
@TableName("`order`")
@Schema(description = "订单信息")
public class Order {

    @TableId(type = IdType.AUTO)
    @Schema(description = "订单 ID", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @Schema(description = "订单编号", accessMode = Schema.AccessMode.READ_ONLY)
    private String orderNo;

    @Schema(description = "买家用户 ID")
    private Long buyerId;

    @Schema(description = "卖家用户 ID", accessMode = Schema.AccessMode.READ_ONLY)
    private Long sellerId;

    @Schema(description = "商品 ID")
    private Long productId;

    @Schema(description = "购买数量，必须大于 0", example = "2")
    private Integer quantity;

    @Schema(description = "商品小计，等于单价乘数量", example = "1998.00")
    private BigDecimal productAmount;

    @Schema(description = "运费金额", example = "0.00")
    private BigDecimal freight;

    @Schema(description = "实付总金额", example = "1998.00")
    private BigDecimal totalAmount;

    @Schema(description = "收货地址")
    private String address;

    @Schema(description = "快递单号")
    private String expressNo;

    @Schema(description = "订单状态：0=待支付，1=待发货，2=待收货，3=已完成，4=已取消")
    private Integer status;

    @Schema(description = "买家视角是否隐藏：0=否，1=是", example = "0")
    private Integer buyerDeleted;

    @Schema(description = "卖家视角是否隐藏：0=否，1=是", example = "0")
    private Integer sellerDeleted;

    @Schema(description = "取消类型：1=买家取消，2=卖家拒绝发货")
    private Integer cancelType;

    @Schema(description = "取消或拒绝原因")
    private String cancelReason;

    @Schema(description = "支付时间", accessMode = Schema.AccessMode.READ_ONLY)
    private Date payTime;

    @Schema(description = "发货时间", accessMode = Schema.AccessMode.READ_ONLY)
    private Date deliverTime;

    @Schema(description = "完成时间", accessMode = Schema.AccessMode.READ_ONLY)
    private Date finishTime;

    @Schema(description = "创建时间", accessMode = Schema.AccessMode.READ_ONLY)
    private Date createTime;

    @TableField(exist = false)
    @Schema(description = "商品标题")
    private String productTitle;

    @TableField(exist = false)
    @Schema(description = "商品封面图")
    private String productImage;

    @TableField(exist = false)
    @Schema(description = "买家昵称")
    private String buyerNickname;

    @TableField(exist = false)
    @Schema(description = "卖家昵称")
    private String sellerNickname;
}
