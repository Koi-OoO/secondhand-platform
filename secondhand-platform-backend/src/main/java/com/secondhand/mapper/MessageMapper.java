package com.secondhand.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.secondhand.entity.Message;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 私信数据访问接口。
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /**
     * 聚合查询「我」参与的所有会话摘要。
     * 按对方用户分组，返回：对方 id（peerId）、该会话最后一条消息 id（lastId）、我在该会话的未读数（unreadCount）。
     *
     * @param me 当前用户 id
     * @return 每个会话一行的摘要列表
     */
    List<Map<String, Object>> selectConversationSummaries(@Param("me") Long me);
}
