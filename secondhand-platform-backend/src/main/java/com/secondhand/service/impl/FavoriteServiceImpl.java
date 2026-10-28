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
import com.secondhand.service.NotificationService;
import com.secondhand.util.Result;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 收藏服务实现类
 */
@Service  // 标识为Service层组件
public class FavoriteServiceImpl implements FavoriteService {
    private static final Logger log = LoggerFactory.getLogger(FavoriteServiceImpl.class);
    private static final DefaultRedisScript<Long> RELEASE_NOTIFICATION_MARKER = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);

    @Autowired  // 注入收藏Mapper
    private FavoriteMapper favoriteMapper;
    @Autowired  // 注入商品Mapper
    private ProductMapper productMapper;
    @Autowired  // 注入商品图片Mapper
    private ProductImageMapper productImageMapper;
    @Autowired  // 注入通知服务（商品被收藏时通知卖家）
    private NotificationService notificationService;
    @Autowired
    private StringRedisTemplate notificationRedis;

    /**
     * 添加收藏
     */
    @Override
    @Transactional  // 开启事务
    public Result addFavorite(Long userId, Long productId) {
        // 1. 校验参数
        if (userId == null || productId == null || productId <= 0) {
            return Result.error("参数错误");
        }

        // 2. 查询商品，确认商品存在且在售
        Product product = productMapper.selectById(productId);
        if (product == null) {
            return Result.error("商品不存在");
        }
        // 商品状态不是1（在售），不能收藏
        if (product.getStatus() != 1) {
            return Result.error("商品已下架或已售完");
        }

        // 3. 已收藏时直接返回成功，不重复插入和累加收藏数
        if (favoriteMapper.countByUserAndProduct(userId, productId) > 0) {
            return Result.success("已收藏");
        }

        // 4. 保存收藏记录，并把商品收藏数加一
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);  // 用户ID
        favorite.setProductId(productId);  // 商品ID
        favorite.setCreateTime(new Date());  // 收藏时间
        favoriteMapper.insert(favorite);  // 插入数据库

        // 商品收藏数+1
        productMapper.incrementLikeCount(productId, 1);

        // 5. 收藏他人商品时通知卖家，收藏自己的商品不通知
        if (!product.getSellerId().equals(userId)) {
            notifySellerIfNeeded(userId, product);
        }

        // 6. 返回收藏结果
        return Result.success("收藏成功");
    }

    private void notifySellerIfNeeded(Long userId, Product product) {
        // 1. 生成收藏通知去重标记
        String key = "notification:favorite:" + userId + ":" + product.getId();
        String token = UUID.randomUUID().toString();
        try {
            // 2. 原子占位：取消收藏不清除该标记，反复收藏也不会重复提醒
            if (!Boolean.TRUE.equals(notificationRedis.opsForValue()
                    .setIfAbsent(key, token, 24, TimeUnit.HOURS))) {
                return;
            }

            // 3. 收藏事务回滚时释放本次标记，避免没有收藏成功却占用提醒名额
            if (TransactionSynchronizationManager.isActualTransactionActive()
                    && TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_ROLLED_BACK) {
                            return;
                        }
                        try {
                            // 比较令牌，避免删掉其他请求的标记
                            notificationRedis.execute(RELEASE_NOTIFICATION_MARKER, Collections.singletonList(key), token);
                        } catch (RuntimeException exception) {
                            log.warn("收藏通知回滚标记清理失败，等待自动过期：productId={}", product.getId(), exception);
                        }
                    }
                });
            }

            // 4. 通知卖家商品被收藏
            notificationService.notifyFavorite(product.getSellerId(), userId, product.getId(), product.getTitle());
        } catch (RuntimeException exception) {
            // 5. 通知属于附属功能，Redis 异常时不影响收藏和收藏数更新
            log.warn("收藏通知暂未发送：productId={}", product.getId(), exception);
        }
    }

    /**
     * 取消收藏
     */
    @Override
    @Transactional  // 开启事务
    public Result removeFavorite(Long userId, Long productId) {
        // 1. 校验参数
        if (userId == null || productId == null || productId <= 0) {
            return Result.error("参数错误");
        }

        // 2. 按用户和商品删除收藏记录
        LambdaQueryWrapper<Favorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Favorite::getUserId, userId)
                .eq(Favorite::getProductId, productId);
        int deleted = favoriteMapper.delete(wrapper);

        // 3. 只有确实删除了收藏记录，才把商品收藏数减一
        if (deleted > 0) {
            productMapper.incrementLikeCount(productId, -1);
        }

        // 4. 返回取消结果
        return Result.success("已取消收藏");
    }

    /**
     * 查询我的收藏列表（分页）
     */
    @Override
    public Result getMyFavorites(Long userId, Integer page, Integer size) {
        // 1. 规范页码和每页条数
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1) {
            size = 12;
        }

        // 2. 按用户ID筛选，按收藏时间倒序分页查询
        LambdaQueryWrapper<Favorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Favorite::getUserId, userId)
                .orderByDesc(Favorite::getCreateTime);

        Page<Favorite> pageRequest = new Page<>(page, size);
        IPage<Favorite> pageResult = favoriteMapper.selectPage(pageRequest, wrapper);

        // 3. 当前页没有收藏时直接返回空分页结果
        List<Favorite> favorites = pageResult.getRecords();
        if (favorites.isEmpty()) {
            return Result.success(pageResult);
        }

        // 4. 提取商品ID，一次批量查询商品
        List<Long> productIds = new ArrayList<>();
        for (Favorite favorite : favorites) {
            productIds.add(favorite.getProductId());
        }
        List<Product> products = productMapper.selectBatchIds(productIds);
        Map<Long, Product> productMap = new LinkedHashMap<>();
        for (Product product : products) {
            productMap.put(product.getId(), product);
        }

        // 5. 一次批量查询商品图片，并按商品ID分组
        List<ProductImage> allImages = productImageMapper.selectByProductIds(productIds);
        Map<Long, List<ProductImage>> imageMap = new LinkedHashMap<>();
        for (ProductImage image : allImages) {
            List<ProductImage> images = imageMap.get(image.getProductId());
            if (images == null) {
                images = new ArrayList<>();
                imageMap.put(image.getProductId(), images);
            }
            images.add(image);
        }

        // 6. 组装每条收藏记录和对应商品信息
        List<Map<String, Object>> records = new ArrayList<>();
        for (Favorite favorite : favorites) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("favoriteId", favorite.getId());  // 收藏记录ID
            item.put("createTime", favorite.getCreateTime());  // 收藏时间

            Product product = productMap.get(favorite.getProductId());
            if (product != null) {
                // 设置商品图片列表，并清空重复的图片URL列表
                product.setImages(imageMap.getOrDefault(product.getId(), Collections.emptyList()));
                product.setImageUrls(null);
                item.put("product", product);  // 商品信息
            }
            records.add(item);
        }

        // 7. 构造分页结果
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);  // 数据列表
        data.put("total", pageResult.getTotal());  // 总记录数
        data.put("current", pageResult.getCurrent());  // 当前页码
        data.put("pages", pageResult.getPages());  // 总页数
        return Result.success(data);
    }

    /**
     * 查询是否已收藏
     */
    @Override
    public Result isFavorited(Long userId, Long productId) {
        // 1. 参数不完整时按未收藏处理
        if (userId == null || productId == null || productId <= 0) {
            return Result.success(false);
        }

        // 2. 查询收藏记录数量，大于0表示已收藏
        boolean favorited = favoriteMapper.countByUserAndProduct(userId, productId) > 0;
        return Result.success(favorited);
    }
}
