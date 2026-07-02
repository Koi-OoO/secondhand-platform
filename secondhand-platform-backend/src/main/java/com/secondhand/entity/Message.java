package com.secondhand.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import lombok.Data;

/**
 * 私信消息实体。
 */
@Data  // Lombok：自动生成getter/setter/toString等
@TableName("message")  // 映射数据库message表
@Schema(description = "私信消息")  // Swagger文档说明
public class Message {

    @TableId(type = IdType.AUTO)  // 主键自增
    @Schema(description = "消息 ID", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @Schema(description = "发送者用户 ID", accessMode = Schema.AccessMode.READ_ONLY)  // 由后端按当前登录用户填充
    private Long fromUserId;

    @Schema(description = "接收者用户 ID")
    private Long toUserId;

    @Schema(description = "关联商品 ID（围绕某商品聊天，可空）")
    private Long productId;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "接收方是否已读", accessMode = Schema.AccessMode.READ_ONLY)
    private Boolean isRead;  // Boolean包装类型，映射is_read列（下划线转驼峰）

    @Schema(description = "创建时间", accessMode = Schema.AccessMode.READ_ONLY)
    private Date createTime;

    // ===== 以下为组装会话/聊天列表时填充的非持久化字段 =====

    @TableField(exist = false)  // 非数据库字段，不参与持久化
    @Schema(description = "对方昵称")
    private String peerNickname;

    @TableField(exist = false)
    @Schema(description = "对方头像")
    private String peerAvatar;
}
