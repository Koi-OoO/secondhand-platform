package com.secondhand.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.secondhand.entity.Evaluation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 评价数据访问接口。
 */
@Mapper
public interface EvaluationMapper extends BaseMapper<Evaluation> {

    /**
     * 计算某个用户收到的平均评分。
     */
    @Select("SELECT COALESCE(AVG(rating), 0) FROM evaluation WHERE to_user_id = #{userId}")
    double avgRatingByUser(@Param("userId") Long userId);
}
