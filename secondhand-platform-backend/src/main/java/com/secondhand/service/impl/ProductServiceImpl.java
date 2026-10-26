package com.secondhand.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.secondhand.entity.Category;
import com.secondhand.entity.Product;
import com.secondhand.entity.ProductImage;
import com.secondhand.mapper.CategoryMapper;
import com.secondhand.mapper.ProductImageMapper;
import com.secondhand.mapper.ProductMapper;
import com.secondhand.service.ProductService;
import com.secondhand.util.CacheUtil;
import com.secondhand.util.Result;
import com.secondhand.search.ProductSearchCriteria;
import com.secondhand.search.ProductSearchService;
import com.secondhand.search.ProductSearchUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商品服务实现类
 */
@Service  // 标识为Service层组件
public class ProductServiceImpl implements ProductService {

    // 日志记录器
    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

    // ==================== Redis键名常量定义 ====================
    private static final String VIEW_COUNT_KEY = "product:views:";      // 商品浏览量计数器前缀
    private static final String PRODUCT_LOCK_KEY = "product:lock:";     // 商品分布式锁前缀
    private static final String PRODUCT_CACHE_KEY = "product:detail:";  // 商品详情缓存前缀
    private static final String HOT_KEYWORD_KEY = "hot:keywords";       // 热门搜索词有序集合键

    // ==================== 超时与锁配置常量 ====================
    private static final long LOCK_WAIT_TIME = 3;        // 分布式锁等待时间（秒）
    private static final long LOCK_LEASE_TIME = 10;      // 分布式锁持有时间（秒）
    private static final long DETAIL_LOGIC_TTL = 300;    // 缓存逻辑过期时间（秒）= 5分钟
    private static final long DETAIL_PHYSIC_TTL = 1800;  // 缓存物理过期时间（秒）= 30分钟
    private static final long DETAIL_TTL_JITTER = 120;   // 缓存过期时间随机抖动（秒）

    // ==================== 依赖注入 ====================
    @Autowired  // 注入商品Mapper
    private ProductMapper productMapper;

    @Autowired  // 注入商品图片Mapper
    private ProductImageMapper productImageMapper;

    @Autowired  // 注入分类Mapper
    private CategoryMapper categoryMapper;

    @Autowired  // 注入Redis模板
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired  // 注入Redisson客户端（分布式锁）
    private RedissonClient redissonClient;

    @Autowired  // 注入缓存工具类
    private CacheUtil cacheUtil;
    @Autowired
    private com.secondhand.websocket.ProductChangePublisher productChanges;

    private void productChanged(Long id) {
        if (productChanges != null) productChanges.publish(id);
    }

    // 仅控制商品详情的缓存读写；关闭后仍保留浏览统计和交易锁。
    @Value("${app.cache.product-detail.enabled:true}")
    private boolean productDetailCacheEnabled = true;

    // 与商品广场复用全文搜索；ES 关闭时允许传统服务启动，但 AI 查询明确报错。
    @Autowired
    private ObjectProvider<ProductSearchService> productSearchService;

    /**
     * 分页查询商品列表（支持多条件筛选）
     */
    @Override
    public Result pageProducts(Integer page, Integer size,
                               Long sellerId, Integer categoryId,
                               String keyword, BigDecimal minPrice,
                               BigDecimal maxPrice, Integer conditionLevel,
                               String sort) {
        return pageProductsInternal(page, size, sellerId, categoryId, keyword,
                minPrice, maxPrice, conditionLevel, sort, true);
    }

    /**
     * 为 AI 查询商品分页结果，不记录热搜词。
     */
    @Override
    public Result pageProductsForAi(Integer page, Integer size, Integer categoryId,
                                    String keyword, BigDecimal minPrice,
                                    BigDecimal maxPrice, Integer conditionLevel,
                                    String sort) {
        ProductSearchCriteria criteria = new ProductSearchCriteria(page, size, keyword, categoryId,
                minPrice, maxPrice, conditionLevel, sort);
        ProductSearchService search = productSearchService.getIfAvailable();
        if (search == null) throw new ProductSearchUnavailableException("商品全文搜索尚未启用", null);
        return Result.success(search.search(criteria));
    }

    /**
     * 分页查询商品列表内部实现。
     */
    private Result pageProductsInternal(Integer page, Integer size,
                                        Long sellerId, Integer categoryId,
                                        String keyword, BigDecimal minPrice,
                                        BigDecimal maxPrice, Integer conditionLevel,
                                        String sort, boolean recordHotKeyword) {
        // 分页参数校正：页码默认1
        if (page == null || page < 1) {
            page = 1;
        }
        // 分页参数校正：每页条数默认12
        if (size == null || size < 1) {
            size = 12;
        }
        // 限制最大每页50条
        if (size > 50) {
            size = 50;
        }

        // 有搜索关键词时，更新热搜榜（ZSet结构，搜索一次分数+1）
        if (recordHotKeyword && keyword != null && !keyword.trim().isEmpty()) {
            redisTemplate.opsForZSet().incrementScore(HOT_KEYWORD_KEY, keyword.trim(), 1);
        }

        // 构建分页对象
        Page<Product> pageObj = new Page<>(page, size);
        // 商品状态：卖家查全部，访客查在售（status=1）
        Integer status = sellerId != null ? null : 1;
        // 执行多条件分页查询
        IPage<Product> result = productMapper.selectPageWithDetails(
                pageObj, sellerId, categoryId, keyword, minPrice, maxPrice,
                conditionLevel, status, sort
        );

        // 查询结果不为空时，批量填充商品图片
        if (!result.getRecords().isEmpty()) {
            fillImages(result.getRecords());
        }

        return Result.success(result);
    }

    /**
     * 查询商品详情（带缓存防击穿）
     */
    @Override
    public Result getDetail(Long productId) {
        return getDetailInternal(productId, true);
    }

    /**
     * 为 AI 查询商品详情，不增加浏览量。
     */
    @Override
    public Result getDetailForAi(Long productId) {
        return getDetailInternal(productId, false);
    }

    /**
     * 查询商品详情内部实现。
     */
    private Result getDetailInternal(Long productId, boolean recordViewCount) {
        // 商品ID校验
        if (productId == null || productId <= 0) {
            return Result.error("商品 ID 无效");
        }
        // 开启时使用逻辑过期缓存；关闭时复用同一数据库加载器进行对照测试。
        Product product = productDetailCacheEnabled ? cacheUtil.queryWithLogicalExpire(
                PRODUCT_CACHE_KEY, productId, Product.class,
                this::loadProductDetail,  // 缓存未命中时的回源加载器
                DETAIL_LOGIC_TTL,
                DETAIL_PHYSIC_TTL + (long) (Math.random() * DETAIL_TTL_JITTER))
                : loadProductDetail(productId);
        // 商品不存在、已删除，或 AI 查询时商品不是在售状态
        boolean invisible = recordViewCount
                ? (product != null && product.getStatus() != null && product.getStatus() == 4)
                : (product != null && !Integer.valueOf(1).equals(product.getStatus()));
        if (product == null || invisible) {
            return Result.error("商品不存在");
        }
        // 增加浏览量（异步，写Redis）
        if (recordViewCount) {
            incrementViewCount(productId);
        }
        return Result.success(product);
    }

    /**
     * 商品详情回源加载器（从数据库查询并补齐图片）
     */
    private Product loadProductDetail(Long productId) {
        // 查询商品详情（含卖家、分类信息）
        Product product = productMapper.selectByIdWithDetails(productId);
        // 商品存在时，补齐图片列表
        if (product != null) {
            fillSingleImages(product);
        }
        return product;
    }

    /**
     * 发布新商品
     */
    @Override
    @Transactional  // 开启事务
    public Result publish(Long userId, Product product, List<String> imageUrls) {
        // ========== 1. 参数基础校验 ==========
        // 用户未登录
        if (userId == null) {
            return Result.error("用户未登录");
        }
        // 商品信息为空
        if (product == null) {
            return Result.error("商品信息不能为空");
        }

        if (product.getTradeLocation() == null || product.getTradeLocation().trim().isEmpty()
                || product.getTradeLocation().trim().length() > 200) {
            return Result.error("请填写面交地点，长度不超过200个字符");
        }
        product.setTradeLocation(product.getTradeLocation().trim());

        // 标题校验：2-100字符
        if (product.getTitle() == null || product.getTitle().trim().length() < 2
                || product.getTitle().trim().length() > 100) {
            return Result.error("商品标题长度需在 2 到 100 个字符之间");
        }

        // 价格校验：必须大于0
        if (product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return Result.error("售价必须大于 0");
        }

        // 库存校验：必须大于0
        if (product.getStock() == null || product.getStock() <= 0) {
            return Result.error("库存必须大于 0");
        }

        // 图片校验：1-9张
        if (imageUrls == null || imageUrls.isEmpty()) {
            return Result.error("请至少上传一张商品图片");
        }
        if (imageUrls.size() > 9) {
            return Result.error("最多上传 9 张图片");
        }
        // 图片URL不能为空
        for (String imageUrl : imageUrls) {
            if (imageUrl == null || imageUrl.trim().isEmpty()) {
                return Result.error("商品图片地址不能为空");
            }
        }

        // 成色等级校验：1-4
        if (product.getConditionLevel() == null || product.getConditionLevel() < 1
                || product.getConditionLevel() > 4) {
            return Result.error("成色等级无效，需在 1 到 4 之间");
        }

        // 新商品必须选择具体分类，避免整机与配件继续混在同一大类中。
        Result categoryValidation = validateCategory(product.getCategoryId(), null, true);
        if (categoryValidation != null) {
            return categoryValidation;
        }

        // 交易地点校验（可选，但填写的话不能为空白）
        if (product.getTradeLocation() != null && product.getTradeLocation().trim().isEmpty()) {
            product.setTradeLocation(null);
        }

        // ========== 2. 补齐系统字段 ==========
        product.setSellerId(userId);      // 卖家ID
        product.setStatus(1);              // 状态：1-在售
        product.setViewCount(0);           // 初始浏览量
        product.setLikeCount(0);           // 初始点赞量
        product.setCreateTime(new Date()); // 创建时间
        product.setUpdateTime(new Date()); // 更新时间

        // ========== 3. 写入数据库 ==========
        // 保存商品主表，生成商品ID
        productMapper.insert(product);

        // 批量保存商品图片
        for (int i = 0; i < imageUrls.size(); i++) {
            ProductImage image = new ProductImage();
            image.setProductId(product.getId());  // 关联商品ID
            image.setUrl(imageUrls.get(i));        // 图片URL
            image.setSort(i);                      // 排序（首图为0）
            image.setCreateTime(new Date());
            productImageMapper.insert(image);
        }

        log.info("用户 {} 发布了商品 [{}]: {}", userId, product.getId(), product.getTitle());
        return Result.success("发布成功");
    }

    /**
     * 编辑商品信息
     */
    @Override
    @Transactional
    public Result update(Long userId, Product product) {
        // ========== 1. 参数校验 ==========
        if (userId == null || product == null || product.getId() == null) {
            return Result.error("参数错误");
        }

        // ========== 2. 查询原商品并校验权限 ==========
        Product existing = productMapper.selectById(product.getId());
        // 商品不存在或已删除
        if (existing == null || existing.getStatus() == 4) {
            return Result.error("商品不存在");
        }
        // 只能修改自己的商品
        if (!existing.getSellerId().equals(userId)) {
            return Result.error("无权修改他人发布的商品");
        }
        // 只能修改已下架的商品（防止在售商品被篡改）
        if (existing.getStatus() != 2) {
            return Result.error("请先下架商品再修改");
        }

        // ========== 3. 字段合法性校验 ==========
        Result validation = validateProductUpdate(product, existing.getCategoryId());
        if (validation != null) {
            return validation;
        }

        // ========== 4. 禁止修改敏感字段 ==========
        product.setSellerId(null);   // 卖家ID不可改
        product.setStatus(null);      // 状态不可直接改
        product.setViewCount(null);   // 浏览量不可直接改
        product.setLikeCount(null);   // 点赞量不可直接改
        product.setCreateTime(null);  // 创建时间不可改
        product.setUpdateTime(new Date());  // 只更新修改时间

        // ========== 5. 更新商品主表 ==========
        productMapper.updateById(product);

        // ========== 6. 更新图片列表（全量覆盖） ==========
        List<String> newUrls = product.getImageUrls();
        if (newUrls != null) {
            // 删除原图片
            productImageMapper.deleteByProductId(product.getId());
            // 重新插入新图片
            for (int i = 0; i < newUrls.size(); i++) {
                ProductImage image = new ProductImage();
                image.setProductId(product.getId());
                image.setUrl(newUrls.get(i));
                image.setSort(i);
                image.setCreateTime(new Date());
                productImageMapper.insert(image);
            }
        }


        // ========== 7. 清理缓存 ==========
        cacheUtil.delete(PRODUCT_CACHE_KEY + product.getId());
        redisTemplate.delete(PRODUCT_CACHE_KEY + product.getId());

        log.info("用户 {} 更新了商品 [{}]", userId, product.getId());
        productChanged(product.getId());
        return Result.success("更新成功");
    }

    /**
     * 校验商品编辑请求参数
     */
    private Result validateProductUpdate(Product product, Integer originalCategoryId) {
        if (product.getTradeLocation() != null) {
            String location = product.getTradeLocation().trim();
            if (location.isEmpty() || location.length() > 200) {
                return Result.error("请填写面交地点，长度不超过200个字符");
            }
            product.setTradeLocation(location);
        }

        // 标题校验
        if (product.getTitle() != null) {
            int titleLength = product.getTitle().trim().length();
            if (titleLength < 2 || titleLength > 100) {
                return Result.error("商品标题长度需在 2 到 100 个字符之间");
            }
        }
        // 价格校验
        if (product.getPrice() != null && product.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return Result.error("售价必须大于 0");
        }
        // 库存校验
        if (product.getStock() != null && product.getStock() <= 0) {
            return Result.error("库存必须大于 0");
        }
        // 成色等级校验
        if (product.getConditionLevel() != null
                && (product.getConditionLevel() < 1 || product.getConditionLevel() > 4)) {
            return Result.error("成色等级无效，需在 1 到 4 之间");
        }
        Result categoryValidation = validateCategory(product.getCategoryId(), originalCategoryId, false);
        if (categoryValidation != null) {
            return categoryValidation;
        }

        // 图片列表校验
        List<String> imageUrls = product.getImageUrls();
        if (imageUrls != null) {
            if (imageUrls.isEmpty()) {
                return Result.error("请至少保留一张商品图片");
            }
            if (imageUrls.size() > 9) {
                return Result.error("最多上传 9 张图片");
            }
            for (String imageUrl : imageUrls) {
                if (imageUrl == null || imageUrl.trim().isEmpty()) {
                    return Result.error("商品图片地址不能为空");
                }
            }
        }
        return null;  // 校验通过
    }

    /**
     * 新发布或更换分类要求叶子分类；旧商品可以保留原分类，兼容分类细分前的数据。
     * 编辑请求省略分类表示不修改，由 MyBatis 保留原值。
     */
    private Result validateCategory(Integer categoryId, Integer originalCategoryId, boolean required) {
        if (categoryId == null) {
            return required ? Result.error("请选择商品分类") : null;
        }
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            return Result.error("所选分类不存在");
        }
        if (!required && Objects.equals(categoryId, originalCategoryId)) {
            return null;
        }
        if (!categoryMapper.selectByParentId(categoryId).isEmpty()) {
            return Result.error("请选择具体子分类");
        }
        return null;
    }

    /**
     * 下架商品
     */
    @Override
    public Result offShelf(Long userId, Long productId) {
        // 参数校验
        if (userId == null || productId == null || productId <= 0) {
            return Result.error("参数错误");
        }
        // 获取分布式锁
        String lockKey = PRODUCT_LOCK_KEY + productId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试获取锁
            if (!lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                return Result.error("操作过于频繁，请稍后重试");
            }

            // 查询商品
            Product product = productMapper.selectById(productId);
            // 商品不存在或已删除
            if (product == null || product.getStatus() == 4) {
                return Result.error("商品不存在");
            }
            // 只能操作自己的商品
            if (!product.getSellerId().equals(userId)) {
                return Result.error("无权操作他人发布的商品");
            }
            // 只有在售状态才能下架
            if (product.getStatus() != 1) {
                return Result.error("商品当前状态不允许下架");
            }

            // 更新状态为已下架(2)
            product.setStatus(2);
            product.setUpdateTime(new Date());
            productMapper.updateById(product);
            // 清除缓存
            redisTemplate.delete(PRODUCT_CACHE_KEY + productId);

            log.info("用户 {} 下架了商品 [{}]", userId, productId);
            productChanged(productId);
            return Result.success("下架成功");
        } catch (InterruptedException ex) {
            // 中断异常，恢复中断状态
            Thread.currentThread().interrupt();
            return Result.error("操作被中断，请重试");
        } finally {
            // 释放锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 上架商品
     */
    @Override
    public Result onShelf(Long userId, Long productId) {
        // 参数校验
        if (userId == null || productId == null || productId <= 0) {
            return Result.error("参数错误");
        }
        // 获取分布式锁
        String lockKey = PRODUCT_LOCK_KEY + productId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试获取锁
            if (!lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                return Result.error("操作过于频繁，请稍后重试");
            }

            // 查询商品
            Product product = productMapper.selectById(productId);
            // 商品不存在或已删除
            if (product == null || product.getStatus() == 4) {
                return Result.error("商品不存在");
            }
            // 只能操作自己的商品
            if (!product.getSellerId().equals(userId)) {
                return Result.error("无权操作他人发布的商品");
            }
            // 只有下架状态才能上架
            if (product.getStatus() != 2) {
                return Result.error("商品当前状态不允许上架");
            }
            // 库存不足无法上架
            if (product.getStock() == null || product.getStock() <= 0) {
                return Result.error("库存不足，无法上架");
            }

            // 更新状态为在售(1)
            product.setStatus(1);
            product.setUpdateTime(new Date());
            productMapper.updateById(product);
            // 清除缓存
            redisTemplate.delete(PRODUCT_CACHE_KEY + productId);

            log.info("用户 {} 上架了商品 [{}]", userId, productId);
            productChanged(productId);
            return Result.success("上架成功");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Result.error("操作被中断，请重试");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 卖家删除自己的商品
     * 仅限已下架(status=2)或已售出(status=3)的商品。
     * 软删除：将status设为4(已删除)，不物理删除数据。
     */
    @Override
    @Transactional
    public Result deleteProduct(Long userId, Long productId) {
        if (userId == null || productId == null || productId <= 0) {
            return Result.error("参数错误");
        }

        Product product = productMapper.selectById(productId);
        if (product == null) {
            return Result.error("商品不存在");
        }
        if (!product.getSellerId().equals(userId)) {
            return Result.error("无权操作他人发布的商品");
        }
        if (product.getStatus() == 1) {
            return Result.error("请先下架商品再删除");
        }
        if (product.getStatus() == 4) {
            return Result.error("商品已删除");
        }

        // 软删除
        product.setStatus(4);
        product.setUpdateTime(new Date());
        productMapper.updateById(product);


        cacheUtil.delete(PRODUCT_CACHE_KEY + productId);
        redisTemplate.delete(PRODUCT_CACHE_KEY + productId);

        log.info("用户 {} 删除了商品 [{}]", userId, productId);
        productChanged(productId);
        return Result.success("删除成功");
    }

    /**
     * 查询卖家商品列表
     */
    @Override
    public Result getSellerProducts(Long sellerId, Integer page, Integer size, Long viewerId) {
        // 页码默认1
        if (page == null || page < 1) {
            page = 1;
        }
        // 每页条数默认12
        if (size == null || size < 1) {
            size = 12;
        }
        // 限制最大50条
        if (size > 50) {
            size = 50;
        }
        // 本人查看自己主页返回全部状态，访客只看在售
        boolean ownerView = viewerId != null && viewerId.equals(sellerId);
        Integer status = ownerView ? null : 1;  // 本人看全部，访客看在售

        // 分页查询
        Page<Product> pageObj = new Page<>(page, size);
        IPage<Product> result = productMapper.selectPageWithDetails(
                pageObj, sellerId, null, null, null, null, null, status, "latest");
        // 补齐图片
        if (!result.getRecords().isEmpty()) {
            fillImages(result.getRecords());
        }
        return Result.success(result);
    }

    /**
     * 举报商品
     */
    @Override
    public Result reportProduct(Long userId, Long productId, String reason, String detail) {
        // 保留旧接口兼容请求；待具备持久化记录和处理流程后再开放。
        return Result.error("举报功能暂未开放");
    }

    /**
     * 查询热门搜索关键词
     */
    @Override
    public Result getHotKeywords() {
        // 从ZSet中按分数降序取前10个
        Set<Object> top = redisTemplate.opsForZSet().reverseRange(HOT_KEYWORD_KEY, 0, 9);
        if (top == null || top.isEmpty()) {
            return Result.success(Collections.emptyList());
        }
        List<String> keywords = top.stream().map(Object::toString).collect(Collectors.toList());
        return Result.success(keywords);
    }

    /**
     * 增加商品浏览量（异步写入Redis）
     */
    @Override
    public void incrementViewCount(Long productId) {
        String key = VIEW_COUNT_KEY + productId;
        // Redis原子自增
        redisTemplate.opsForValue().increment(key, 1);
        // 设置过期时间2小时
        redisTemplate.expire(key, 2, TimeUnit.HOURS);
    }

    /**
     * 定时同步商品浏览量到数据库（每5分钟执行一次）
     */
    @Override
    @Scheduled(fixedRate = 300_000)  // 5分钟 = 300000毫秒
    public void syncViewCounts() {
        // ========== 1. 扫描所有浏览量相关的Redis Key ==========
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions()
                .match(VIEW_COUNT_KEY + "*")  // 匹配前缀
                .count(100)                   // 每次扫描100个
                .build();

        // 使用SCAN命令扫描（非阻塞）
        try (Cursor<byte[]> cursor = redisTemplate.getConnectionFactory().getConnection().scan(options)) {
            while (cursor.hasNext()) {
                keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
            }
        } catch (Exception ex) {
            log.warn("扫描浏览量键失败：{}", ex.getMessage());
            return;
        }

        // 没有需要同步的键
        if (keys.isEmpty()) {
            return;
        }

        // ========== 2. Lua脚本：原子性地获取并删除计数 ==========
        String lua = "local v = redis.call('GET', KEYS[1]) redis.call('DEL', KEYS[1]) return v";
        DefaultRedisScript<Object> script = new DefaultRedisScript<>(lua, Object.class);

        // ========== 3. 批量同步到数据库 ==========
        int synced = 0;
        for (String key : keys) {
            try {
                // 从key中解析商品ID
                Long productId = Long.parseLong(key.replace(VIEW_COUNT_KEY, ""));
                // 获取并删除浏览量计数
                Object countObj = redisTemplate.execute(script, Collections.singletonList(key));
                if (countObj instanceof Number) {
                    int count = ((Number) countObj).intValue();
                    if (count > 0) {
                        // 更新数据库
                        productMapper.incrementViewCount(productId, count);
                        synced++;
                    }
                }
            } catch (Exception ex) {
                log.warn("同步浏览量失败，键={}：{}", key, ex.getMessage());
            }
        }

        if (synced > 0) {
            log.info("成功同步了 {} 个商品的浏览量", synced);
        }
    }

    /**
     * 根据ID查询商品
     */
    @Override
    public Product getProductById(Long productId) {
        if (productId == null) {
            return null;
        }
        return productMapper.selectById(productId);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 为单个商品补齐图片列表（详情页用）
     */
    private void fillSingleImages(Product product) {
        // 查询该商品的所有图片
        List<ProductImage> images = productImageMapper.selectByProductId(product.getId());
        product.setImages(images);
    }

    /**
     * 批量为商品列表补齐图片（列表页用）
     */
    private void fillImages(List<Product> products) {
        // 列表为空直接返回
        if (products.isEmpty()) {
            return;
        }

        // 收集所有商品ID
        List<Long> productIds = products.stream().map(Product::getId).collect(Collectors.toList());

        // 一次查询所有商品图片
        List<ProductImage> allImages = productImageMapper.selectByProductIds(productIds);

        // 按商品ID分组
        Map<Long, List<ProductImage>> imageMap = allImages.stream()
                .collect(Collectors.groupingBy(ProductImage::getProductId));

        // 为每个商品设置图片列表
        for (Product product : products) {
            product.setImages(imageMap.getOrDefault(product.getId(), Collections.emptyList()));
        }
    }
}
