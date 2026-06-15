package com.secondhand.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.secondhand.entity.Favorite;
import com.secondhand.entity.Product;
import com.secondhand.entity.ProductImage;
import com.secondhand.mapper.FavoriteMapper;
import com.secondhand.mapper.ProductImageMapper;
import com.secondhand.mapper.ProductMapper;
import com.secondhand.service.FavoriteService;
import com.secondhand.util.Result;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 收藏服务实现类
 */
@Service  // 标识为Service层组件
public class FavoriteServiceImpl implements FavoriteService {

    @Autowired  // 注入收藏Mapper
    private FavoriteMapper favoriteMapper;
    @Autowired  // 注入商品Mapper
    private ProductMapper productMapper;
    @Autowired  // 注入商品图片Mapper
    private ProductImageMapper productImageMapper;

    /**
     * 添加收藏
     */
    @Override
    @Transactional  // 开启事务
    public Result addFavorite(Long userId, Long productId) {
        // 参数校验
        if (userId == null || productId == null || productId <= 0) {
            return Result.error("参数错误");
        }
        // 查询商品是否存在
        Product product = productMapper.selectById(productId);
        if (product == null) {
            return Result.error("商品不存在");
        }
        // 商品状态不是1（在售），不能收藏
        if (product.getStatus() != 1) {
            return Result.error("商品已下架或已售完");
        }

        // 检查是否已收藏
        if (favoriteMapper.countByUserAndProduct(userId, productId) > 0) {
            return Result.success("已收藏");  // 已收藏则直接返回成功
        }

        // 创建收藏记录
        Favorite fav = new Favorite();
        fav.setUserId(userId);  // 用户ID
        fav.setProductId(productId);  // 商品ID
        fav.setCreateTime(new Date());  // 收藏时间
        favoriteMapper.insert(fav);  // 插入数据库

        // 商品收藏数+1
        productMapper.incrementLikeCount(productId, 1);
        return Result.success("收藏成功");
    }

    /**
     * 取消收藏
     */
    @Override
    @Transactional  // 开启事务
    public Result removeFavorite(Long userId, Long productId) {
        // 参数校验
        if (userId == null || productId == null || productId <= 0) {
            return Result.error("参数错误");
        }
        // 构建删除条件
        LambdaQueryWrapper<Favorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Favorite::getUserId, userId)
                .eq(Favorite::getProductId, productId);
        // 删除收藏记录
        int deleted = favoriteMapper.delete(wrapper);
        // 如果删除了记录，商品收藏数-1
        if (deleted > 0) {
            productMapper.incrementLikeCount(productId, -1);
        }
        return Result.success("已取消收藏");
    }

    /**
     * 查询我的收藏列表（分页）
     */
    @Override
    public Result getMyFavorites(Long userId, Integer page, Integer size) {
        // 页码默认1
        if (page == null || page < 1) {
            page = 1;
        }
        // 每页条数默认12
        if (size == null || size < 1) {
            size = 12;
        }

        // 构建查询条件：按用户ID筛选，按收藏时间倒序
        LambdaQueryWrapper<Favorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Favorite::getUserId, userId)
                .orderByDesc(Favorite::getCreateTime);

        // 分页查询收藏记录
        Page<Favorite> pageObj = new Page<>(page, size);
        IPage<Favorite> result = favoriteMapper.selectPage(pageObj, wrapper);

        // 获取收藏记录列表
        List<Favorite> favs = result.getRecords();
        // 无数据直接返回
        if (favs.isEmpty()) {
            return Result.success(result);
        }

        // 提取商品ID列表
        List<Long> productIds = favs.stream()
                .map(Favorite::getProductId)
                .collect(Collectors.toList());

        // 批量查询商品信息
        List<Product> products = productMapper.selectBatchIds(productIds);
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        // 批量查询商品图片
        List<ProductImage> allImages = productImageMapper.selectByProductIds(productIds);
        Map<Long, List<ProductImage>> imageMap = allImages.stream()
                .collect(Collectors.groupingBy(ProductImage::getProductId));

        // 组装返回数据：每条收藏包含收藏记录+商品信息
        List<Map<String, Object>> list = new ArrayList<>();
        for (Favorite fav : favs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("favoriteId", fav.getId());  // 收藏记录ID
            item.put("createTime", fav.getCreateTime());  // 收藏时间

            // 获取商品信息
            Product p = productMap.get(fav.getProductId());
            if (p != null) {
                // 设置商品图片列表
                p.setImages(imageMap.getOrDefault(p.getId(), Collections.emptyList()));
                p.setImageUrls(null);  // 清空URL列表，避免重复
                item.put("product", p);  // 商品信息
            }
            list.add(item);
        }

        // 构造分页结果
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", list);  // 数据列表
        data.put("total", result.getTotal());  // 总记录数
        data.put("current", result.getCurrent());  // 当前页码
        data.put("pages", result.getPages());  // 总页数
        return Result.success(data);
    }

    /**
     * 查询是否已收藏
     */
    @Override
    public Result isFavorited(Long userId, Long productId) {
        // 参数为空直接返回false
        if (userId == null || productId == null || productId <= 0) {
            return Result.success(false);
        }
        // 查询收藏数量是否大于0
        boolean favorited = favoriteMapper.countByUserAndProduct(userId, productId) > 0;
        return Result.success(favorited);
    }
}