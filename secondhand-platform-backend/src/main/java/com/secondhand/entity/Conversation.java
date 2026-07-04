package com.secondhand.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import lombok.Data;

/**
 * 私信会话实体。
 */
@Data
@TableName("conversation")
@Schema(description = "私信会话")
public class Conversation {

    @TableId(type = IdType.AUTO)
    @Schema(description = "会话 ID", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @Schema(description = "较小的用户 ID")
    private Long user1Id;

    @Schema(description = "较大的用户 ID")
    private Long user2Id;

    @Schema(description = "关联商品 ID（从商品详情发起时记录，可空）")
    private Long productId;

    @Schema(description = "最后一条消息 ID，可空")
    private Long lastMessageId;

    @Schema(description = "会话最后活跃时间")
    private Date lastTime;

    @Schema(description = "创建时间", accessMode = Schema.AccessMode.READ_ONLY)
    private Date createTime;

    @Schema(description = "更新时间", accessMode = Schema.AccessMode.READ_ONLY)
    private Date updateTime;
}
