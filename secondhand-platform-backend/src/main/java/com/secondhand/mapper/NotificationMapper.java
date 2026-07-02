package com.secondhand.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.secondhand.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知数据访问接口。
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
