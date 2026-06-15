package com.secondhand.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.secondhand.entity.ProductImage;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 商品图片数据访问接口。
 */
@Mapper
public interface ProductImageMapper extends BaseMapper<ProductImage> {

    /**
     * 查询单个商品的全部图片，按 sort 升序返回。
     */
    @Select("SELECT * FROM product_image WHERE product_id = #{productId} ORDER BY sort ASC")
    List<ProductImage> selectByProductId(@Param("productId") Long productId);

    /**
     * 批量查询多个商品的图片。
     */
    List<ProductImage> selectByProductIds(@Param("productIds") List<Long> productIds);

    /**
     * 删除某个商品的全部图片记录。
     */
    @Delete("DELETE FROM product_image WHERE product_id = #{productId}")
    int deleteByProductId(@Param("productId") Long productId);
}
