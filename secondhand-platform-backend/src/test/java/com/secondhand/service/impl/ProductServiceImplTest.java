package com.secondhand.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.secondhand.entity.Category;
import com.secondhand.entity.Product;
import com.secondhand.mapper.CategoryMapper;
import com.secondhand.mapper.ProductImageMapper;
import com.secondhand.mapper.ProductMapper;
import com.secondhand.util.CacheUtil;
import com.secondhand.util.Result;
import com.secondhand.search.ProductSearchCriteria;
import com.secondhand.search.ProductSearchService;
import com.secondhand.search.ProductSearchUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceImplTest {

    private ProductMapper productMapper;
    private ProductImageMapper productImageMapper;
    private CategoryMapper categoryMapper;
    private RedisTemplate<String, Object> redisTemplate;
    private RedissonClient redissonClient;
    private CacheUtil cacheUtil;
    private ProductServiceImpl service;
    private com.secondhand.websocket.ProductChangePublisher productChanges;

    @BeforeEach
    void setUp() {
        productMapper = mock(ProductMapper.class);
        productImageMapper = mock(ProductImageMapper.class);
        categoryMapper = mock(CategoryMapper.class);
        redisTemplate = mock(RedisTemplate.class);
        redissonClient = mock(RedissonClient.class);
        cacheUtil = mock(CacheUtil.class);
        service = new ProductServiceImpl();
        ReflectionTestUtils.setField(service, "productMapper", productMapper);
        ReflectionTestUtils.setField(service, "productImageMapper", productImageMapper);
        ReflectionTestUtils.setField(service, "categoryMapper", categoryMapper);
        ReflectionTestUtils.setField(service, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(service, "redissonClient", redissonClient);
        ReflectionTestUtils.setField(service, "cacheUtil", cacheUtil);
        productChanges = mock(com.secondhand.websocket.ProductChangePublisher.class);
        ReflectionTestUtils.setField(service, "productChanges", productChanges);
    }

    @Test
    void reportingIsUnavailableWithoutPretendingToAcceptOrWritingDedupKeys() {
        Result result = service.reportProduct(7L, 40L, "虚假信息", "测试说明");
        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMessage()).isEqualTo("举报功能暂未开放");
        verifyNoInteractions(productMapper, redisTemplate, cacheUtil);
    }

    @Test
    void publishingRequiresMeetupLocation() {
        Product product = validProduct();
        product.setTradeLocation(" ");
        product.setStock(1);
        when(categoryMapper.selectById(3)).thenReturn(validCategory());
        Result result = service.publish(7L, product, List.of("/uploads/test.jpg"));
        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMessage()).contains("面交地点");
    }

    @Test
    void publishRequiresStockFieldForInventoryContract() {
        Field stockField = requireField(Product.class, "stock");

        assertThat(stockField.getType()).isEqualTo(Integer.class);
    }

    @Test
    void publishRejectsNonPositiveStockBeforeWriting() {
        Product product = validProduct();
        setRequiredField(product, "stock", 0);

        Result result = service.publish(7L, product, List.of("https://img.example.com/1.png"));

        assertThat(result.getCode()).isEqualTo(500);
        verify(productMapper, never()).insert(any(Product.class));
    }

    @Test
    void publishPersistsStockWhenValid() {
        Product product = validProduct();
        setRequiredField(product, "stock", 6);
        when(categoryMapper.selectById(3)).thenReturn(validCategory());

        Result result = service.publish(7L, product, List.of("https://img.example.com/1.png"));

        assertThat(result.getCode()).isEqualTo(200);
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productMapper).insert(productCaptor.capture());
        assertThat(readRequiredField(productCaptor.getValue(), "stock")).isEqualTo(6);
        assertThat(productCaptor.getValue().getStatus()).isEqualTo(1);
    }

    @Test
    void publishRequiresCategoryAndDoesNotWriteOnFailure() {
        Product product = validProduct();
        product.setStock(1);
        product.setCategoryId(null);

        Result result = service.publish(7L, product, List.of("/uploads/test.jpg"));

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMessage()).contains("请选择商品分类");
        verify(productMapper, never()).insert(any(Product.class));
    }

    @Test
    void publishRejectsParentCategoryButAcceptsItsLeaf() {
        Product parentProduct = validProduct();
        parentProduct.setStock(1);
        when(categoryMapper.selectById(3)).thenReturn(validCategory());
        Category child = new Category();
        child.setId(30);
        child.setParentId(3);
        when(categoryMapper.selectByParentId(3)).thenReturn(List.of(child));

        Result result = service.publish(7L, parentProduct, List.of("/uploads/test.jpg"));

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMessage()).contains("具体子分类");
        verify(productMapper, never()).insert(any(Product.class));

        Product leafProduct = validProduct();
        leafProduct.setStock(1);
        leafProduct.setCategoryId(30);
        when(categoryMapper.selectById(30)).thenReturn(child);
        assertThat(service.publish(7L, leafProduct, List.of("/uploads/test.jpg")).getCode()).isEqualTo(200);
    }

    @Test
    void categoryMustExistBeforePublishOrCategoryChange() {
        Product product = validProduct();
        product.setStock(1);
        product.setCategoryId(999);
        assertThat(service.publish(7L, product, List.of("/uploads/test.jpg")).getMessage())
                .contains("分类不存在");
        verify(productMapper, never()).insert(any(Product.class));

        prepareLegacyProduct();
        Product edit = new Product();
        edit.setId(40L);
        edit.setCategoryId(999);
        assertThat(service.update(7L, edit).getMessage()).contains("分类不存在");
        verify(productMapper, never()).updateById(any(Product.class));
    }

    @Test
    void editingLegacyParentCategoryCanKeepOriginalOrOmitIt() {
        prepareLegacyProduct();
        when(categoryMapper.selectById(3)).thenReturn(validCategory());
        Category child = new Category();
        child.setId(30);
        when(categoryMapper.selectByParentId(3)).thenReturn(List.of(child));
        Product edit = new Product();
        edit.setId(40L);
        edit.setCategoryId(3);
        edit.setPrice(new BigDecimal("99.00"));

        assertThat(service.update(7L, edit).getCode()).isEqualTo(200);
        verify(productChanges).publish(40L);
        // 旧商品保留大类时无需重新判断叶子，避免补分类后无法编辑。
        verify(categoryMapper, never()).selectByParentId(3);
        Product partialEdit = new Product();
        partialEdit.setId(40L);
        partialEdit.setPrice(new BigDecimal("88.00"));
        assertThat(service.update(7L, partialEdit).getCode()).isEqualTo(200);
    }

    @Test
    void changingCategoryRejectsAnotherParentAndAllowsLeaf() {
        prepareLegacyProduct();
        Category parent = new Category();
        parent.setId(4);
        Category child = new Category();
        child.setId(41);
        child.setParentId(4);
        when(categoryMapper.selectById(4)).thenReturn(parent);
        when(categoryMapper.selectByParentId(4)).thenReturn(List.of(child));
        Product edit = new Product();
        edit.setId(40L);
        edit.setCategoryId(4);

        assertThat(service.update(7L, edit).getCode()).isEqualTo(500);
        verify(productMapper, never()).updateById(any(Product.class));

        edit.setCategoryId(41);
        when(categoryMapper.selectById(41)).thenReturn(child);
        assertThat(service.update(7L, edit).getCode()).isEqualTo(200);
        verify(productMapper).updateById(edit);
    }

    private void prepareLegacyProduct() {
        Product existing = new Product();
        existing.setId(40L);
        existing.setSellerId(7L);
        existing.setCategoryId(3);
        existing.setStatus(2);
        when(productMapper.selectById(40L)).thenReturn(existing);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getDetailReturnsProductWhenCacheUtilResolvesIt() {
        Product product = new Product();
        product.setId(40L);
        product.setStatus(1);
        when(cacheUtil.<Long, Product>queryWithLogicalExpire(
                any(), eq(40L), eq(Product.class), any(), anyLong(), anyLong()))
                .thenReturn(product);
        ValueOperations<String, Object> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);

        Result result = service.getDetail(40L);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isSameAs(product);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getDetailReturnsNotFoundWhenCacheUtilReturnsNull() {
        when(cacheUtil.<Long, Product>queryWithLogicalExpire(
                any(), eq(40L), eq(Product.class), any(), anyLong(), anyLong()))
                .thenReturn(null);

        Result result = service.getDetail(40L);

        assertThat(result.getCode()).isEqualTo(500);
    }

    @Test
    @SuppressWarnings("unchecked")
    void pageProductsForAiUsesEsWithoutLegacyQueryOrHotKeyword() {
        ProductSearchService search = mock(ProductSearchService.class);
        ObjectProvider<ProductSearchService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(search);
        ReflectionTestUtils.setField(service, "productSearchService", provider);
        ProductSearchCriteria criteria = new ProductSearchCriteria(1, 12, "苹果手机", 3,
                new BigDecimal("1"), new BigDecimal("2000"), 2, "latest");
        Page<Product> expected = new Page<>(1, 12);
        when(search.search(criteria)).thenReturn(expected);
        Result result = service.pageProductsForAi(1, 12, 3, "苹果手机",
                new BigDecimal("1"), new BigDecimal("2000"), 2, "latest");
        assertThat(result.getData()).isSameAs(expected);
        verify(search).search(criteria);
        verifyNoInteractions(productMapper, redisTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aiSearchDisabledNeverFallsBackToMysql() {
        ObjectProvider<ProductSearchService> provider = mock(ObjectProvider.class);
        ReflectionTestUtils.setField(service, "productSearchService", provider);
        assertThatThrownBy(() -> service.pageProductsForAi(1, 10, null, "手机", null, null, null, "latest"))
                .isInstanceOf(ProductSearchUnavailableException.class);
        verifyNoInteractions(productMapper, redisTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getDetailForAiReturnsCachedProductWithoutRecordingViewCount() {
        Product product = new Product();
        product.setId(40L);
        product.setStatus(1);
        when(cacheUtil.<Long, Product>queryWithLogicalExpire(
                any(), eq(40L), eq(Product.class), any(), anyLong(), anyLong()))
                .thenReturn(product);

        Result result = service.getDetailForAi(40L);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isSameAs(product);
        verify(redisTemplate, never()).opsForValue();
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4})
    @SuppressWarnings("unchecked")
    void getDetailForAiHidesProductsThatAreNotOnSale(int status) {
        Product product = new Product();
        product.setId(40L);
        product.setStatus(status);
        when(cacheUtil.<Long, Product>queryWithLogicalExpire(
                any(), eq(40L), eq(Product.class), any(), anyLong(), anyLong()))
                .thenReturn(product);

        Result result = service.getDetailForAi(40L);

        assertThat(result.getCode()).isEqualTo(500);
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void aiSearchRejectsInvalidSizeInsteadOfSilentlyClamping() {
        assertThatThrownBy(() -> service.pageProductsForAi(1, 100, null, null, null, null, null, "latest"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(productMapper);
    }

    @Test
    void pageProductsRecordsHotKeywordForPublicSearch() {
        Page<Product> emptyPage = new Page<>(1, 12);
        emptyPage.setRecords(Collections.emptyList());
        when(productMapper.selectPageWithDetails(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(emptyPage);
        ZSetOperations<String, Object> zSetOperations = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        service.pageProducts(1, 12, null, null, "switch", null, null, null, "latest");

        verify(zSetOperations).incrementScore("hot:keywords", "switch", 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getDetailRecordsViewCountForPublicDetail() {
        Product product = new Product();
        product.setId(40L);
        when(cacheUtil.<Long, Product>queryWithLogicalExpire(
                any(), eq(40L), eq(Product.class), any(), anyLong(), anyLong()))
                .thenReturn(product);
        ValueOperations<String, Object> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);

        service.getDetail(40L);

        verify(ops).increment("product:views:40", 1);
    }

    @Test
    void getSellerProductsShowsOnlyOnSaleToVisitors() {
        Page<Product> emptyPage = new Page<>(1, 12);
        emptyPage.setRecords(Collections.emptyList());
        when(productMapper.selectPageWithDetails(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(emptyPage);

        service.getSellerProducts(6L, 1, 12, 99L);

        verify(productMapper).selectPageWithDetails(
                any(), eq(6L), any(), any(), any(), any(), any(), eq(1), any());
    }

    @Test
    void getSellerProductsShowsAllStatusesToOwner() {
        Page<Product> emptyPage = new Page<>(1, 12);
        emptyPage.setRecords(Collections.emptyList());
        when(productMapper.selectPageWithDetails(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(emptyPage);

        service.getSellerProducts(6L, 1, 12, 6L);

        verify(productMapper).selectPageWithDetails(
                any(), eq(6L), any(), any(), any(), any(), any(), isNull(), any());
    }

    private Product validProduct() {
        Product product = new Product();
        product.setTitle("Nintendo Switch");
        product.setTradeLocation("学校东门");
        product.setDescription("Lightly used console");
        product.setPrice(new BigDecimal("1499.00"));
        product.setOriginalPrice(new BigDecimal("1999.00"));
        product.setCategoryId(3);
        product.setConditionLevel(2);
        return product;
    }

    private Category validCategory() {
        Category category = new Category();
        category.setId(3);
        category.setName("Game");
        return category;
    }

    private Field requireField(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name);
        } catch (NoSuchFieldException ex) {
            throw new AssertionError("Expected field " + type.getSimpleName() + "." + name + " to exist", ex);
        }
    }

    private void setRequiredField(Object target, String name, Object value) {
        requireField(target.getClass(), name);
        ReflectionTestUtils.setField(target, name, value);
    }

    private Object readRequiredField(Object target, String name) {
        requireField(target.getClass(), name);
        return ReflectionTestUtils.getField(target, name);
    }
}
