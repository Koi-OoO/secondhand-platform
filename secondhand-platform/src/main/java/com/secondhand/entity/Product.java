package com.secondhand.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import lombok.Data;

/**
 * 商品实体。
 */
@Data
@TableName("product")
@Schema(description = "商品信息")
public class Product {

    @TableId(type = IdType.AUTO)
    @Schema(description = "商品 ID", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @Schema(description = "卖家用户 ID", example = "1", required = true)
    private Long sellerId;

    @Schema(description = "商品标题", example = "iPhone 15 Pro 256G", required = true)
    private String title;

    @Schema(description = "商品描述", example = "使用三个月，无磕碰，配件齐全")
    private String description;

    @Schema(description = "售价，必须大于 0", example = "5999.00", required = true)
    private BigDecimal price;

    @Schema(description = "可售库存，必须大于 0", example = "3", required = true)
    private Integer stock;

    @Schema(description = "原价，可选", example = "8999.00")
    private BigDecimal originalPrice;

    @Schema(description = "分类 ID", example = "1")
    private Integer categoryId;

    @Schema(description = "成色等级：1=全新，2=近全新，3=轻度使用，4=明显使用",
            example = "2", allowableValues = {"1", "2", "3", "4"})
    private Integer conditionLevel;

    @Schema(description = "状态：1=在售，2=已下架，3=已售罄", example = "1",
            allowableValues = {"1", "2", "3"})
    private Integer status;

    @Schema(description = "浏览量", example = "128", accessMode = Schema.AccessMode.READ_ONLY)
    private Integer viewCount;

    @Schema(description = "收藏量", example = "15", accessMode = Schema.AccessMode.READ_ONLY)
    private Integer likeCount;

    @Schema(description = "创建时间", accessMode = Schema.AccessMode.READ_ONLY)
    private Date createTime;

    @Schema(description = "更新时间", accessMode = Schema.AccessMode.READ_ONLY)
    private Date updateTime;

    @TableField(exist = false)
    @Schema(description = "商品图片列表")
    private List<ProductImage> images;

    @TableField(exist = false)
    @Schema(description = "卖家昵称")
    private String sellerNickname;

    @TableField(exist = false)
    @Schema(description = "卖家用户名")
    private String sellerUsername;

    @TableField(exist = false)
    @Schema(description = "卖家头像地址")
    private String sellerAvatar;

    @TableField(exist = false)
    @Schema(description = "分类名称")
    private String categoryName;

    @TableField(exist = false)
    @Schema(description = "发布或编辑商品时使用的图片地址列表")
    private List<String> imageUrls;
}
