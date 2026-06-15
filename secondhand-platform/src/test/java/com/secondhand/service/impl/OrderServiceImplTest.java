package com.secondhand.service.impl;

import com.secondhand.entity.BatchOperationResult;
import com.secondhand.entity.Order;
import com.secondhand.entity.OrderBatchShipItem;
import com.secondhand.entity.Product;
import com.secondhand.mapper.OrderMapper;
import com.secondhand.mapper.ProductImageMapper;
import com.secondhand.mapper.ProductMapper;
import com.secondhand.mapper.UserMapper;
import com.secondhand.util.Result;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceImplTest {

    private OrderMapper orderMapper;
    private ProductMapper productMapper;
    private ProductImageMapper productImageMapper;
    private UserMapper userMapper;
    private RedisTemplate<String, Object> redisTemplate;
    private RedissonClient redissonClient;
    private RLock rLock;
    private OrderServiceImpl service;

    @BeforeEach
    void setUp() throws InterruptedException {
        orderMapper = mock(OrderMapper.class);
        productMapper = mock(ProductMapper.class);
        productImageMapper = mock(ProductImageMapper.class);
        userMapper = mock(UserMapper.class);
        redisTemplate = mock(RedisTemplate.class);
        redissonClient = mock(RedissonClient.class);
        rLock = mock(RLock.class);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "productMapper", productMapper);
        ReflectionTestUtils.setField(service, "productImageMapper", productImageMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(service, "redissonClient", redissonClient);
    }

    @Test
    void createOrderRequiresQuantityArgumentInServiceContract() {
        Method method = requireMethod(OrderServiceImpl.class, "createOrder",
                Long.class, Long.class, Integer.class, String.class);

        assertThat(method.getReturnType()).isEqualTo(Result.class);
    }

    @Test
    void createOrderRejectsWhenRequestedQuantityExceedsAvailableStock() {
        Product product = product(40L, 30L, 1, new BigDecimal("99.00"));
        setRequiredField(product, "stock", 1);
        when(productMapper.selectById(40L)).thenReturn(product);
        when(orderMapper.selectCount(any())).thenReturn(0L);

        Result result = invokeCreateOrder(20L, 40L, 2, "Test address");

        assertThat(result.getCode()).isEqualTo(500);
        verify(orderMapper, never()).insert(any(Order.class));
        verify(productMapper, never()).updateById(any(Product.class));
    }

    @Test
    void createOrderPersistsQuantityAndDeductsStock() {
        Product product = product(40L, 30L, 1, new BigDecimal("99.00"));
        setRequiredField(product, "stock", 5);
        when(productMapper.selectById(40L)).thenReturn(product);
        when(orderMapper.selectCount(any())).thenReturn(0L);

        Result result = invokeCreateOrder(20L, 40L, 3, "Test address");

        assertThat(result.getCode()).isEqualTo(200);
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert(orderCaptor.capture());
        assertThat(readRequiredField(orderCaptor.getValue(), "quantity")).isEqualTo(3);
        assertThat(readRequiredField(orderCaptor.getValue(), "productAmount")).isEqualTo(new BigDecimal("297.00"));
        assertThat(readRequiredField(orderCaptor.getValue(), "totalAmount")).isEqualTo(new BigDecimal("297.00"));
        assertThat(readRequiredField(product, "stock")).isEqualTo(2);
    }

    @Test
    void createOrderReturnsErrorWhenLockNotAcquired() throws InterruptedException {
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

        Result result = invokeCreateOrder(20L, 40L, 1, "Test address");

        assertThat(result.getCode()).isEqualTo(500);
        verify(orderMapper, never()).insert(any(Order.class));
        verify(productMapper, never()).updateById(any(Product.class));
    }

    @Test
    void cancelOrderRestoresStockUsingOrderQuantity() {
        Order order = new Order();
        order.setId(10L);
        order.setOrderNo("ORDER-10");
        order.setBuyerId(20L);
        order.setSellerId(30L);
        order.setProductId(40L);
        order.setStatus(1);
        setRequiredField(order, "quantity", 2);

        Product product = product(40L, 30L, 3, new BigDecimal("99.00"));
        setRequiredField(product, "stock", 3);

        when(orderMapper.selectById(10L)).thenReturn(order);
        when(productMapper.selectById(40L)).thenReturn(product);

        Result result = service.cancelOrder(20L, 10L);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(readRequiredField(product, "stock")).isEqualTo(5);
        verify(productMapper).updateById(product);
    }

    @Test
    void rejectOrderRecordsSellerRejectNoticeForBuyerAndRestoresStock() {
        Order order = new Order();
        order.setId(10L);
        order.setOrderNo("ORDER-10");
        order.setBuyerId(20L);
        order.setSellerId(30L);
        order.setProductId(40L);
        order.setStatus(1);
        setRequiredField(order, "quantity", 2);

        Product product = product(40L, 30L, 3, new BigDecimal("99.00"));
        setRequiredField(product, "stock", 3);

        when(orderMapper.selectById(10L)).thenReturn(order);
        when(productMapper.selectById(40L)).thenReturn(product);

        Result result = service.rejectOrder(30L, 10L, "搴撳瓨涓嶈冻");

        assertThat(result.getCode()).isEqualTo(200);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).updateById(orderCaptor.capture());
        Order updatedOrder = orderCaptor.getValue();

        assertThat(updatedOrder.getStatus()).isEqualTo(4);
        assertThat(ReflectionTestUtils.getField(updatedOrder, "cancelType")).isEqualTo(2);
        assertThat(ReflectionTestUtils.getField(updatedOrder, "cancelReason")).isEqualTo("搴撳瓨涓嶈冻");
        assertThat(readRequiredField(product, "stock")).isEqualTo(5);
        verify(productMapper).updateById(product);
    }

    @Test
    void hideBoughtOrderMarksBuyerVisibilityOnly() {
        Order order = completedOrder(10L, 20L, 30L);
        setRequiredField(order, "buyerDeleted", 0);
        setRequiredField(order, "sellerDeleted", 0);
        when(orderMapper.selectById(10L)).thenReturn(order);

        Result result = service.hideBoughtOrder(20L, 10L);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(readRequiredField(order, "buyerDeleted")).isEqualTo(1);
        assertThat(readRequiredField(order, "sellerDeleted")).isEqualTo(0);
        verify(orderMapper).updateById(order);
    }

    @Test
    void hideSoldOrderAllowsCancelledOrder() {
        Order order = completedOrder(10L, 20L, 30L);
        order.setStatus(4);
        setRequiredField(order, "sellerDeleted", 0);
        when(orderMapper.selectById(10L)).thenReturn(order);

        Result result = service.hideSoldOrder(30L, 10L);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(readRequiredField(order, "sellerDeleted")).isEqualTo(1);
        verify(orderMapper).updateById(order);
    }

    @Test
    void batchHideSoldOrdersReturnsMixedSuccessAndFailure() {
        Order allowed = completedOrder(10L, 20L, 30L);
        allowed.setStatus(4);
        setRequiredField(allowed, "sellerDeleted", 0);

        Order blocked = completedOrder(11L, 20L, 30L);
        blocked.setStatus(2);
        setRequiredField(blocked, "sellerDeleted", 0);

        when(orderMapper.selectById(10L)).thenReturn(allowed);
        when(orderMapper.selectById(11L)).thenReturn(blocked);

        Result result = service.batchHideSoldOrders(30L, Arrays.asList(10L, 11L));

        assertThat(result.getCode()).isEqualTo(200);
        BatchOperationResult data = (BatchOperationResult) result.getData();
        assertThat(data.getSuccessIds()).containsExactly(10L);
        assertThat(data.getFailCount()).isEqualTo(1);
    }

    @Test
    void hideSoldOrderRejectsNonCompletedOrder() {
        Order order = completedOrder(10L, 20L, 30L);
        order.setStatus(2);
        setRequiredField(order, "sellerDeleted", 0);
        when(orderMapper.selectById(10L)).thenReturn(order);

        Result result = service.hideSoldOrder(30L, 10L);

        assertThat(result.getCode()).isEqualTo(500);
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    void hideSoldOrderRejectsWrongSeller() {
        Order order = completedOrder(10L, 20L, 30L);
        setRequiredField(order, "sellerDeleted", 0);
        when(orderMapper.selectById(10L)).thenReturn(order);

        Result result = service.hideSoldOrder(31L, 10L);

        assertThat(result.getCode()).isEqualTo(500);
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    void batchRejectOrdersReturnsSuccessIds() {
        Order order = completedOrder(10L, 20L, 30L);
        order.setStatus(1);
        setRequiredField(order, "quantity", 1);
        Product product = product(40L, 30L, 1, new BigDecimal("99.00"));
        setRequiredField(product, "stock", 2);
        when(orderMapper.selectById(10L)).thenReturn(order);
        when(productMapper.selectById(40L)).thenReturn(product);

        Result result = service.batchRejectOrders(30L, Collections.singletonList(10L), "No stock");

        assertThat(result.getCode()).isEqualTo(200);
        BatchOperationResult data = (BatchOperationResult) result.getData();
        assertThat(data.getSuccessIds()).containsExactly(10L);
    }

    @Test
    void batchShipOrdersReturnsFailureWhenTrackingNumberMissing() {
        Order order = completedOrder(10L, 20L, 30L);
        order.setStatus(1);
        when(orderMapper.selectById(10L)).thenReturn(order);

        OrderBatchShipItem item = new OrderBatchShipItem();
        item.setOrderId(10L);
        item.setExpressNo(" ");

        Result result = service.batchShipOrders(30L, Collections.singletonList(item));

        assertThat(result.getCode()).isEqualTo(200);
        BatchOperationResult data = (BatchOperationResult) result.getData();
        assertThat(data.getSuccessCount()).isEqualTo(0);
        assertThat(data.getFailCount()).isEqualTo(1);
    }

    @Test
    void getMyBoughtOrdersAndSoldOrdersMethodsRemainAvailableAfterVisibilityChange() {
        when(orderMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> {
                    IPage<Order> page = invocation.getArgument(0);
                    page.setRecords(Collections.emptyList());
                    page.setTotal(0);
                    return page;
                });

        Result bought = service.getMyBoughtOrders(20L, 1, 12);
        Result sold = service.getMySoldOrders(30L, 1, 12);

        assertThat(bought.getCode()).isEqualTo(200);
        assertThat(sold.getCode()).isEqualTo(200);
        verify(orderMapper, org.mockito.Mockito.times(2))
                .selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    private Product product(Long id, Long sellerId, Integer status, BigDecimal price) {
        Product product = new Product();
        product.setId(id);
        product.setSellerId(sellerId);
        product.setStatus(status);
        product.setPrice(price);
        return product;
    }

    private Order completedOrder(Long id, Long buyerId, Long sellerId) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNo("ORDER-" + id);
        order.setBuyerId(buyerId);
        order.setSellerId(sellerId);
        order.setProductId(40L);
        order.setStatus(3);
        return order;
    }

    private Result invokeCreateOrder(Long buyerId, Long productId, Integer quantity, String address) {
        try {
            Method method = requireMethod(OrderServiceImpl.class, "createOrder",
                    Long.class, Long.class, Integer.class, String.class);
            return (Result) method.invoke(service, buyerId, productId, quantity, address);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Expected quantity-aware createOrder contract", ex);
        }
    }

    private Method requireMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ex) {
            throw new AssertionError("Expected method " + type.getSimpleName() + "." + name + " to exist", ex);
        }
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
