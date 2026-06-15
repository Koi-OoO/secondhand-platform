package com.secondhand.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import lombok.Data;

/**
 * 收藏关系实体。
 */
@Data
@TableName("favorite")
@Schema(description = "用户收藏")
public class Favorite {

    @TableId(type = IdType.AUTO)
    @Schema(description = "收藏 ID", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @Schema(description = "当前会话中的用户 ID", example = "6",
            accessMode = Schema.AccessMode.READ_ONLY)
    private Long userId;

    @Schema(description = "被收藏的商品 ID", example = "10")
    private Long productId;

    @Schema(description = "创建时间", accessMode = Schema.AccessMode.READ_ONLY)
    private Date createTime;
}
