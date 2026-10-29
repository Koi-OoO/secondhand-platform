package com.secondhand.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.secondhand.constant.ProductStatus;
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
import com.secondhand.websocket.ProductChangePublisher;
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
    private ProductChangePublisher productChanges;

    /** 发布商品变更事件，供页面重新查询库存和状态。 */
    private void productChanged(Long productId) {
        if (productChanges != null) {
            productChanges.publish(productId);
        }
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
        // 1. 校正分页参数，默认每页12条，最多50条
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

        // 2. 有关键词时增加热搜分数
        if (keyword != null && !keyword.trim().isEmpty()) {
            redisTemplate.opsForZSet().incrementScore(HOT_KEYWORD_KEY, keyword.trim(), 1);
        }

        // 3. 按筛选条件查询MySQL商品分页
        Page<Product> pageObj = new Page<>(page, size);
        // 传入卖家ID时查询其全部状态，否则只查询在售商品。
        Integer status = sellerId != null ? null : 1;
        // 执行多条件分页查询
        IPage<Product> result = productMapper.selectPageWithDetails(
                pageObj, sellerId, categoryId, keyword, minPrice, maxPrice,
                conditionLevel, status, sort
        );

        // 4. 批量补齐商品图片
        if (!result.getRecords().isEmpty()) {
            fillImages(result.getRecords());
        }

        // 5. 返回分页结果
        return Result.success(result);
    }

    /**
     * 为 AI 查询商品分页结果，不记录热搜词。
     */
    @Override
    public Result pageProductsForAi(Integer page, Integer size, Integer categoryId,
                                    String keyword, BigDecimal minPrice,
                                    BigDecimal maxPrice, Integer conditionLevel,
                                    String sort) {
        // 1. 校验并组装搜索条件
        ProductSearchCriteria criteria = new ProductSearchCriteria(page, size, keyword, categoryId,
                minPrice, maxPrice, conditionLevel, sort);

        // 2. 获取ES搜索服务，未启用时明确报错，不回退到MySQL
        ProductSearchService search = productSearchService.getIfAvailable();
        if (search == null) {
            throw new ProductSearchUnavailableException("商品全文搜索尚未启用", null);
        }

        // 3. 查询ES并返回结果，不记录热搜词
        return Result.success(search.search(criteria));
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
        // 1. 校验商品ID
        if (productId == null || productId <= 0) {
            return Result.error("商品 ID 无效");
        }

        // 2. 根据开关读取缓存，或直接查询数据库
        Product product;
        if (productDetailCacheEnabled) {
            product = cacheUtil.queryWithLogicalExpire(
                    PRODUCT_CACHE_KEY, productId, Product.class,
                    this::loadProductDetail, // 缓存未命中时复用同一数据库加载器
                    DETAIL_LOGIC_TTL,
                    DETAIL_PHYSIC_TTL + (long) (Math.random() * DETAIL_TTL_JITTER));
        } else {
            product = loadProductDetail(productId);
        }

        // 3. 校验商品是否允许当前查询入口查看
        if (product == null) {
            return Result.error("商品不存在");
        }
        boolean invisible;
        if (recordViewCount) {
            // 普通详情不展示已删除商品，仍允许查看下架或售罄商品。
            invisible = ProductStatus.isDeleted(product.getStatus());
        } else {
            // AI只查询在售商品，不展示下架、售罄或缺少状态的商品。
            invisible = !ProductStatus.isOnSale(product.getStatus());
        }
        if (invisible) {
            return Result.error("商品不存在");
        }

        // 4. 普通访问先将浏览量累加到Redis；AI查询不计浏览量
        if (recordViewCount) {
            incrementViewCount(productId);
        }

        // 5. 返回商品详情
        return Result.success(product);
    }

    /**
     * 商品详情回源加载器（从数据库查询并补齐图片）
     */
    private Product loadProductDetail(Long productId) {
        // 1. 查询商品详情，包含卖家和分类信息
        Product product = productMapper.selectByIdWithDetails(productId);

        // 2. 商品存在时补齐图片列表
        if (product != null) {
            fillSingleImages(product);
        }

        // 3. 返回商品；不存在时返回null
        return product;
    }

    /**
     * 发布新商品
     */
    @Override
    @Transactional  // 开启事务
    public Result publish(Long userId, Product product, List<String> imageUrls) {
        // 1. 参数基础校验
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

        // 2. 补齐系统字段
        product.setSellerId(userId);      // 卖家ID
        product.setStatus(ProductStatus.ON_SALE);              // 状态：在售
        product.setViewCount(0);           // 初始浏览量
        product.setLikeCount(0);           // 初始点赞量
        product.setCreateTime(new Date()); // 创建时间
        product.setUpdateTime(new Date()); // 更新时间

        // 3. 写入数据库
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

        // 4. 返回发布结果
        return Result.success("发布成功");
    }

    /**
     * 编辑商品信息
     */
    @Override
    @Transactional
    public Result update(Long userId, Product product) {
        // 1. 参数校验
        if (userId == null || product == null || product.getId() == null) {
            return Result.error("参数错误");
        }

        // 2. 查询原商品并校验权限
        Product existing = productMapper.selectById(product.getId());
        // 商品不存在或已删除
        if (existing == null || ProductStatus.isDeleted(existing.getStatus())) {
            return Result.error("商品不存在");
        }
        // 只能修改自己的商品
        if (!existing.getSellerId().equals(userId)) {
            return Result.error("无权修改他人发布的商品");
        }
        // 只能修改已下架的商品（防止在售商品被篡改）
        if (!ProductStatus.isOffShelf(existing.getStatus())) {
            return Result.error("请先下架商品再修改");
        }

        // 3. 字段合法性校验
        Result validation = validateProductUpdate(product, existing.getCategoryId());
        if (validation != null) {
            return validation;
        }

        // 4. 禁止修改敏感字段
        product.setSellerId(null);   // 卖家ID不可改
        product.setStatus(null);      // 状态不可直接改
        product.setViewCount(null);   // 浏览量不可直接改
        product.setLikeCount(null);   // 点赞量不可直接改
        product.setCreateTime(null);  // 创建时间不可改
        product.setUpdateTime(new Date());  // 只更新修改时间

        // 5. 更新商品主表
        productMapper.updateById(product);

        // 6. 更新图片列表（全量覆盖）
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

        // 7. 清理缓存
        cacheUtil.delete(PRODUCT_CACHE_KEY + product.getId());

        log.info("用户 {} 更新了商品 [{}]", userId, product.getId());

        // 8. 发布商品变更事件，返回修改结果
        productChanged(product.getId());
        return Result.success("更新成功");
    }

    /**
     * 校验商品编辑请求参数
     */
    private Result validateProductUpdate(Product product, Integer originalCategoryId) {
        // 1. 校验并去除面交地点的首尾空格
        if (product.getTradeLocation() != null) {
            String location = product.getTradeLocation().trim();
            if (location.isEmpty() || location.length() > 200) {
                return Result.error("请填写面交地点，长度不超过200个字符");
            }
            product.setTradeLocation(location);
        }

        // 2. 校验标题、价格、库存和成色；未传入的字段不修改
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

        // 3. 校验分类，旧商品可以保留原分类
        Result categoryValidation = validateCategory(product.getCategoryId(), originalCategoryId, false);
        if (categoryValidation != null) {
            return categoryValidation;
        }

        // 4. 校验图片列表；传入时必须保留1至9张有效图片
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

        // 5. 返回null表示校验通过
        return null;  // 校验通过
    }

    /**
     * 新发布或更换分类要求叶子分类；旧商品可以保留原分类，兼容分类细分前的数据。
     * 编辑请求省略分类表示不修改，由 MyBatis 保留原值。
     */
    private Result validateCategory(Integer categoryId, Integer originalCategoryId, boolean required) {
        // 1. 校验是否传入分类；编辑时不传表示保持原值
        if (categoryId == null) {
            return required ? Result.error("请选择商品分类") : null;
        }

        // 2. 查询分类，确认分类存在
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            return Result.error("所选分类不存在");
        }

        // 3. 编辑时允许保留原分类，兼容以前使用大类的商品
        if (!required && Objects.equals(categoryId, originalCategoryId)) {
            return null;
        }

        // 4. 新发布或更换分类时，必须选择没有子分类的具体分类
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
        // 1. 校验用户和商品ID
        if (userId == null || productId == null || productId <= 0) {
            return Result.error("参数错误");
        }

        // 2. 获取商品操作锁
        String lockKey = PRODUCT_LOCK_KEY + productId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试获取锁
            if (!lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                return Result.error("操作过于频繁，请稍后重试");
            }

            // 3. 查询商品，校验卖家身份和当前状态
            Product product = productMapper.selectById(productId);
            // 商品不存在或已删除
            if (product == null || ProductStatus.isDeleted(product.getStatus())) {
                return Result.error("商品不存在");
            }
            // 只能操作自己的商品
            if (!product.getSellerId().equals(userId)) {
                return Result.error("无权操作他人发布的商品");
            }
            // 只有在售状态才能下架
            if (!ProductStatus.isOnSale(product.getStatus())) {
                return Result.error("商品当前状态不允许下架");
            }

            // 4. 更新商品状态和修改时间
            product.setStatus(ProductStatus.OFF_SHELF);
            product.setUpdateTime(new Date());
            productMapper.updateById(product);

            // 5. 清除商品详情缓存，发布变更事件并返回结果
            redisTemplate.delete(PRODUCT_CACHE_KEY + productId);

            log.info("用户 {} 下架了商品 [{}]", userId, productId);
            productChanged(productId);
            return Result.success("下架成功");
        } catch (InterruptedException ex) {
            // 中断异常，恢复中断状态
            Thread.currentThread().interrupt();
            return Result.error("操作被中断，请重试");
        } finally {
            // 6. 最后释放当前线程持有的锁
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
        // 1. 校验用户和商品ID
        if (userId == null || productId == null || productId <= 0) {
            return Result.error("参数错误");
        }

        // 2. 获取商品操作锁
        String lockKey = PRODUCT_LOCK_KEY + productId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试获取锁
            if (!lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                return Result.error("操作过于频繁，请稍后重试");
            }

            // 3. 查询商品，校验卖家身份和当前状态
            Product product = productMapper.selectById(productId);
            // 商品不存在或已删除
            if (product == null || ProductStatus.isDeleted(product.getStatus())) {
                return Result.error("商品不存在");
            }
            // 只能操作自己的商品
            if (!product.getSellerId().equals(userId)) {
                return Result.error("无权操作他人发布的商品");
            }
            // 只有下架状态才能上架
            if (!ProductStatus.isOffShelf(product.getStatus())) {
                return Result.error("商品当前状态不允许上架");
            }
            // 库存不足无法上架
            if (product.getStock() == null || product.getStock() <= 0) {
                return Result.error("库存不足，无法上架");
            }

            // 4. 更新商品状态和修改时间
            product.setStatus(ProductStatus.ON_SALE);
            product.setUpdateTime(new Date());
            productMapper.updateById(product);

            // 5. 清除商品详情缓存，发布变更事件并返回结果
            redisTemplate.delete(PRODUCT_CACHE_KEY + productId);

            log.info("用户 {} 上架了商品 [{}]", userId, productId);
            productChanged(productId);
            return Result.success("上架成功");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Result.error("操作被中断，请重试");
        } finally {
            // 6. 最后释放当前线程持有的锁
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
        // 1. 校验用户和商品ID
        if (userId == null || productId == null || productId <= 0) {
            return Result.error("参数错误");
        }

        // 2. 查询商品，校验归属和商品状态
        Product product = productMapper.selectById(productId);
        if (product == null) {
            return Result.error("商品不存在");
        }
        if (!product.getSellerId().equals(userId)) {
            return Result.error("无权操作他人发布的商品");
        }
        if (ProductStatus.isOnSale(product.getStatus())) {
            return Result.error("请先下架商品再删除");
        }
        if (ProductStatus.isDeleted(product.getStatus())) {
            return Result.error("商品已删除");
        }

        // 3. 将商品标记为已删除，保留数据库记录
        product.setStatus(ProductStatus.DELETED);
        product.setUpdateTime(new Date());
        productMapper.updateById(product);

        // 4. 删除商品详情缓存
        cacheUtil.delete(PRODUCT_CACHE_KEY + productId);

        log.info("用户 {} 删除了商品 [{}]", userId, productId);

        // 5. 发布商品变更事件，返回删除结果
        productChanged(productId);
        return Result.success("删除成功");
    }

    /**
     * 查询卖家商品列表
     */
    @Override
    public Result getSellerProducts(Long sellerId, Integer page, Integer size, Long viewerId) {
        // 1. 校正分页参数，默认每页12条，最多50条
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

        // 2. 判断是否卖家本人，决定查询哪些商品状态
        boolean ownerView = viewerId != null && viewerId.equals(sellerId);
        Integer status = ownerView ? null : 1;  // 本人看全部，访客看在售

        // 3. 分页查询该卖家的商品
        Page<Product> pageObj = new Page<>(page, size);
        IPage<Product> result = productMapper.selectPageWithDetails(
                pageObj, sellerId, null, null, null, null, null, status, "latest");

        // 4. 批量补齐图片并返回分页结果
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
        // 1. 从Redis有序集合中按搜索分数降序取前10个
        Set<Object> top = redisTemplate.opsForZSet().reverseRange(HOT_KEYWORD_KEY, 0, 9);
        if (top == null || top.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        // 2. 转为关键词列表并返回
        List<String> keywords = top.stream().map(Object::toString).collect(Collectors.toList());
        return Result.success(keywords);
    }

    /**
     * 增加商品浏览量（当前请求写Redis，定时任务再同步到MySQL）
     */
    @Override
    public void incrementViewCount(Long productId) {
        String key = VIEW_COUNT_KEY + productId;

        // 1. 将本次浏览量累加到Redis
        redisTemplate.opsForValue().increment(key, 1);

        // 2. 设置2小时过期时间，后续由定时任务同步到MySQL
        redisTemplate.expire(key, 2, TimeUnit.HOURS);
    }

    /**
     * 定时同步商品浏览量到数据库（每5分钟执行一次）
     */
    @Override
    @Scheduled(fixedRate = 300_000)  // 5分钟 = 300000毫秒
    public void syncViewCounts() {
        // 1. 扫描所有浏览量相关的Redis Key
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions()
                .match(VIEW_COUNT_KEY + "*")  // 匹配前缀
                .count(100)                   // 每次扫描100个
                .build();

        // 使用SCAN分批扫描，避免用KEYS一次遍历全部键。
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

        // 2. Lua脚本：原子性地获取并删除计数
        String lua = "local v = redis.call('GET', KEYS[1]) redis.call('DEL', KEYS[1]) return v";
        DefaultRedisScript<Object> script = new DefaultRedisScript<>(lua, Object.class);

        // 3. 逐个读取并清除Redis计数，将浏览量增量写入MySQL
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

        // 4. 记录本次成功同步的商品数量
        if (synced > 0) {
            log.info("成功同步了 {} 个商品的浏览量", synced);
        }
    }

    /**
     * 根据ID查询商品
     */
    @Override
    public Product getProductById(Long productId) {
        // 1. 未传商品ID时返回空结果
        if (productId == null) {
            return null;
        }

        // 2. 查询商品基础信息，不经过详情缓存，也不计浏览量
        return productMapper.selectById(productId);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 为单个商品补齐图片列表（详情页用）
     */
    private void fillSingleImages(Product product) {
        // 查询该商品的图片，补到详情对象中
        List<ProductImage> images = productImageMapper.selectByProductId(product.getId());
        product.setImages(images);
    }

    /**
     * 批量为商品列表补齐图片（列表页用）
     */
    private void fillImages(List<Product> products) {
        // 1. 商品列表为空时直接返回
        if (products.isEmpty()) {
            return;
        }

        // 2. 收集商品ID，一次查询所有图片
        List<Long> productIds = products.stream().map(Product::getId).collect(Collectors.toList());

        // 批量查询，避免逐个商品读取图片。
        List<ProductImage> allImages = productImageMapper.selectByProductIds(productIds);

        // 3. 按商品ID分组，保留查询结果中的图片顺序
        Map<Long, List<ProductImage>> imageMap = allImages.stream()
                .collect(Collectors.groupingBy(ProductImage::getProductId));

        // 4. 给商品补齐图片，无图片时返回空列表
        for (Product product : products) {
            product.setImages(imageMap.getOrDefault(product.getId(), Collections.emptyList()));
        }
    }
}
