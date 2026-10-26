package com.secondhand.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import lombok.Data;

/**
 * 系统/互动通知实体。
 */
@Data  // Lombok：自动生成getter/setter/toString等
@TableName("notification")  // 映射数据库notification表
@Schema(description = "通知")  // Swagger文档说明
public class Notification {

    @TableId(type = IdType.AUTO)  // 主键自增
    @Schema(description = "通知 ID", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @Schema(description = "接收者用户 ID", accessMode = Schema.AccessMode.READ_ONLY)
    private Long userId;

    @Schema(description = "通知类型：order=订单，favorite=收藏，system=系统")
    private String type;

    @Schema(description = "通知标题")
    private String title;

    @Schema(description = "通知内容")
    private String content;

    @Schema(description = "关联业务 ID（如订单 ID）")
    private Long relatedId;

    @Schema(description = "互动发起者公开用户 ID，历史通知可能为空")
    private Long actorUserId;

    @Schema(description = "通知产生时的发起者昵称")
    private String actorNickname;

    @Schema(description = "通知产生时的发起者头像")
    private String actorAvatar;

    @Schema(description = "是否已读", accessMode = Schema.AccessMode.READ_ONLY)
    private Boolean isRead;  // Boolean包装类型，映射is_read列（下划线转驼峰）

    @Schema(description = "创建时间", accessMode = Schema.AccessMode.READ_ONLY)
    private Date createTime;
}
