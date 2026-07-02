package com.secondhand.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import lombok.Data;

/**
 * 商品图片实体。
 */
@Data
@TableName("product_image")
@Schema(description = "商品图片")
public class ProductImage {

    @TableId(type = IdType.AUTO)
    @Schema(description = "图片 ID", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @Schema(description = "关联商品 ID", example = "1", required = true)
    private Long productId;

    @Schema(description = "图片地址", example = "https://example.com/images/product1.jpg", required = true)
    private String url;

    @Schema(description = "排序值，0 表示封面图", example = "0")
    private Integer sort;

    @Schema(description = "创建时间", accessMode = Schema.AccessMode.READ_ONLY)
    private Date createTime;
}
