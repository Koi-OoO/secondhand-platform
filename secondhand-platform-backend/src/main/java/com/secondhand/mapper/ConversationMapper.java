package com.secondhand.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.secondhand.entity.Conversation;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 私信会话数据访问接口。
 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    /**
     * 根据规范化后的用户对查询会话。
     */
    Conversation selectByPair(@Param("user1Id") Long user1Id, @Param("user2Id") Long user2Id);

    /** 原子创建空会话；冲突时保留已有消息摘要，仅补全缺失的关联商品。 */
    int ensureExists(Conversation conversation);

    /** 原子写入消息摘要；并发提交时只让更大的消息 ID 成为最后一条。 */
    int upsertAfterMessage(Conversation conversation);

    /**
     * 查询「我」的会话摘要，包含空会话。
     */
    List<Map<String, Object>> selectConversationSummaries(@Param("me") Long me);
}
