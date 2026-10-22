package com.secondhand.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.secondhand.entity.Evaluation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评价数据访问接口。
 */
@Mapper
public interface EvaluationMapper extends BaseMapper<Evaluation> {

}
