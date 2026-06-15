package com.secondhand.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.secondhand.entity.Category;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 分类数据访问接口。
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

    /**
     * 查询指定父分类下的直接子分类。
     */
    @Select("SELECT * FROM category WHERE parent_id = #{parentId} ORDER BY sort ASC")
    List<Category> selectByParentId(@Param("parentId") Integer parentId);

    /**
     * 查询所有顶级分类。
     */
    @Select("SELECT * FROM category WHERE parent_id = 0 ORDER BY sort ASC")
    List<Category> selectTopCategories();
}
