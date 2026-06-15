package com.secondhand.service;

import com.secondhand.entity.Product;
import com.secondhand.util.Result;
import java.math.BigDecimal;
import java.util.List;

/**
 * 商品服务接口
 * 定义商品模块的核心业务能力，包括商品查询、发布、编辑、
 * 上下架、浏览量统计、举报和热门搜索等功能。
 */
public interface ProductService {

    /**
     * 分页查询商品列表
     *
     * 业务场景：用于首页、搜索页、分类页、卖家主页等商品列表展示场景，
     * 支持按卖家、分类、关键词、价格区间、成色和排序方式组合筛选。
     *
     * @param page 页码
     * @param size 每页条数
     * @param sellerId 卖家 ID
     * @param categoryId 分类 ID
     * @param keyword 搜索关键词
     * @param minPrice 最低价格
     * @param maxPrice 最高价格
     * @param conditionLevel 成色等级
     * @param sort 排序方式
     * @return Result 商品分页结果
     */
    Result pageProducts(Integer page, Integer size,
                        Long sellerId, Integer categoryId,
                        String keyword, BigDecimal minPrice,
                        BigDecimal maxPrice, Integer conditionLevel,
                        String sort);

    /**
     * 获取商品详情
     *
     * 业务逻辑：查询商品完整信息，并在成功访问后记录一次浏览量。
     *
     * @param productId 商品 ID
     * @return Result 商品详情结果
     */
    Result getDetail(Long productId);

    /**
     * 发布商品
     *
     * @param userId 发布人用户 ID
     * @param product 商品主体信息
     * @param imageUrls 商品图片地址列表
     * @return Result 发布结果
     */
    Result publish(Long userId, Product product, List<String> imageUrls);

    /**
     * 编辑商品信息
     *
     * @param userId 当前用户 ID
     * @param product 待更新商品信息
     * @return Result 更新结果
     */
    Result update(Long userId, Product product);

    /**
     * 下架商品
     *
     * @param userId 当前用户 ID
     * @param productId 商品 ID
     * @return Result 下架结果
     */
    Result offShelf(Long userId, Long productId);

    /**
     * 重新上架商品
     *
     * @param userId 当前用户 ID
     * @param productId 商品 ID
     * @return Result 上架结果
     */
    Result onShelf(Long userId, Long productId);

    /**
     * 根据主键查询商品基础信息
     *
     * @param productId 商品 ID
     * @return Product 商品实体
     */
    Product getProductById(Long productId);

    /**
     * 记录商品浏览量
     *
     * 说明：浏览量先累加到 Redis，再由定时任务异步回刷数据库，
     * 以减少高并发场景下的数据库写入压力。
     *
     * @param productId 商品 ID
     */
    void incrementViewCount(Long productId);

    /**
     * 同步 Redis 中的浏览量到数据库
     *
     * 说明：通常由定时任务触发执行。
     */
    void syncViewCounts();

    /**
     * 举报商品
     *
     * @param userId 举报人用户 ID
     * @param productId 商品 ID
     * @param reason 举报原因
     * @param detail 举报补充说明
     * @return Result 举报结果
     */
    Result reportProduct(Long userId, Long productId, String reason, String detail);

    /**
     * 获取热门搜索关键词
     *
     * @return Result 热门关键词列表
     */
    Result getHotKeywords();

    /**
     * 查询某个卖家的商品列表
     *
     * 访客只看到该卖家的在售商品；卖家本人查看自己主页时返回全部状态（含下架、已售）。
     *
     * @param sellerId 卖家 ID
     * @param page 页码
     * @param size 每页条数
     * @param viewerId 当前登录用户 ID，未登录为 null
     * @return Result 商品分页结果
     */
    Result getSellerProducts(Long sellerId, Integer page, Integer size, Long viewerId);
}
