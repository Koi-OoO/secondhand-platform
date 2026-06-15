package com.secondhand.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.secondhand.entity.Product;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 商品数据访问接口
 * 负责商品表以及商品关联展示字段的数据库查询与更新，
 * 包括商品搜索、详情查询、浏览量回写和收藏数维护等操作。
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 分页查询商品详情列表
     *
     * 业务说明：该方法通常用于首页商品流、搜索结果页、卖家主页等场景，
     * 支持按卖家、分类、关键词、价格区间、成色和排序方式进行筛选。
     *
     * @param page 分页对象
     * @param sellerId 卖家 ID
     * @param categoryId 分类 ID
     * @param keyword 搜索关键词
     * @param minPrice 最低价格
     * @param maxPrice 最高价格
     * @param conditionLevel 成色等级
     * @param status 商品状态
     * @param sort 排序方式
     * @return IPage<Product> 商品分页结果
     */
    IPage<Product> selectPageWithDetails(
            Page<Product> page,
            @Param("sellerId") Long sellerId,
            @Param("categoryId") Integer categoryId,
            @Param("keyword") String keyword,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("conditionLevel") Integer conditionLevel,
            @Param("status") Integer status,
            @Param("sort") String sort
    );

    /**
     * 查询单个商品详情
     *
     * 业务说明：除商品主表字段外，还会关联卖家和分类等展示信息，
     * 供商品详情页直接使用。
     *
     * @param productId 商品 ID
     * @return Product 商品详情实体，不存在时返回 null
     */
    Product selectByIdWithDetails(@Param("productId") Long productId);

    /**
     * 原子增加商品浏览量
     *
     * @param productId 商品 ID
     * @param count 增加数量
     * @return int 受影响行数
     */
    @Update("UPDATE product SET view_count = view_count + #{count} WHERE id = #{productId}")
    int incrementViewCount(@Param("productId") Long productId, @Param("count") int count);

    /**
     * 原子增减商品收藏数
     *
     * @param productId 商品 ID
     * @param count 增减数量，正数表示增加，负数表示减少
     * @return int 受影响行数
     */
    @Update("UPDATE product SET like_count = like_count + #{count} WHERE id = #{productId}")
    int incrementLikeCount(@Param("productId") Long productId, @Param("count") int count);
}
