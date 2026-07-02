package com.secondhand.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import java.util.List;
import lombok.Data;

/**
 * 商品分类实体。
 */
@Data
@TableName("category")
@Schema(description = "商品分类")
public class Category {

    @TableId(type = IdType.AUTO)
    @Schema(description = "分类 ID", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
    private Integer id;

    @Schema(description = "分类名称", example = "手机数码", required = true)
    private String name;

    @Schema(description = "父分类 ID，0 表示顶级分类", example = "0")
    private Integer parentId;

    @Schema(description = "同级排序值", example = "1")
    private Integer sort;

    @Schema(description = "创建时间", accessMode = Schema.AccessMode.READ_ONLY)
    private Date createTime;

    @TableField(exist = false)
    @Schema(description = "子分类列表")
    private List<Category> children;
}
