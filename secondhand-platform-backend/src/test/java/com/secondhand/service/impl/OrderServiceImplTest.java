package com.secondhand.service.impl;

import com.secondhand.entity.BatchOperationResult;
import com.secondhand.entity.Order;
import com.secondhand.entity.OrderBatchShipItem;
import com.secondhand.entity.Product;
import com.secondhand.entity.Evaluation;
import com.secondhand.mapper.EvaluationMapper;
import com.secondhand.mapper.OrderMapper;
import com.secondhand.mapper.ProductImageMapper;
import com.secondhand.mapper.ProductMapper;
import com.secondhand.mapper.UserMapper;
import com.secondhand.service.NotificationService;
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
    private NotificationService notificationService;
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
        notificationService = mock(NotificationService.class);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(orderMapper.selectOne(any())).thenAnswer(invocation -> {
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Order> q=invocation.getArgument(0);
            if(q==null) return null;
            q.getSqlSegment();
            Long id=(Long)q.getParamNameValuePairs().values().iterator().next();
            return orderMapper.selectById(id);
        });
        service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "productMapper", productMapper);
        ReflectionTestUtils.setField(service, "productImageMapper", productImageMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(service, "redissonClient", redissonClient);
        ReflectionTestUtils.setField(service, "notificationService", notificationService);
        ReflectionTestUtils.setField(service, "evaluationMapper", mock(EvaluationMapper.class));
    }

    @Test
    void deliveryRequiresBuyerConfirmedTimeForSingleAndBatchRequests() {
        Order order=completedOrder(10L,20L,30L);order.setStatus(1);order.setMeetupTime(null);
        when(orderMapper.selectById(10L)).thenReturn(order);
        assertThat(service.shipOrder(30L,10L,null).getCode()).isNotEqualTo(200);
        OrderBatchShipItem item=new OrderBatchShipItem();item.setOrderId(10L);
        BatchOperationResult batch=(BatchOperationResult)service.batchShipOrders(30L,Collections.singletonList(item)).getData();
        assertThat(batch.getSuccessCount()).isZero();assertThat(order.getStatus()).isEqualTo(1);
        verify(orderMapper,never()).updateById(any(Order.class));
        org.mockito.Mockito.verifyNoInteractions(notificationService);
    }

    @Test
    void sellerReminderRequiresOwnWaitingOrderAndHasDailyCooldown() {
        Order order=completedOrder(10L,20L,30L);order.setStatus(2);
        when(orderMapper.selectById(10L)).thenReturn(order);
        when(orderMapper.update(any(),any())).thenReturn(1);
        Result denied=ReflectionTestUtils.invokeMethod(service,"remindBuyer",99L,10L);
        assertThat(denied.getCode()).isNotEqualTo(200);
        Result success=ReflectionTestUtils.invokeMethod(service,"remindBuyer",30L,10L);
        assertThat(success.getCode()).isEqualTo(200);
        Result repeated=ReflectionTestUtils.invokeMethod(service,"remindBuyer",30L,10L);
        assertThat(repeated.getCode()).isNotEqualTo(200);
        verify(notificationService).notifyUser(org.mockito.ArgumentMatchers.eq(20L),org.mockito.ArgumentMatchers.eq("order"),anyString(),anyString(),org.mockito.ArgumentMatchers.eq(10L));
    }
    @Test
    void buyerIssuePausesRemindersAndOnlyBuyerCanCompleteAfterResolving() {
        Order order=completedOrder(10L,20L,30L);order.setStatus(2);
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(orderMapper.selectById(10L)).thenReturn(order);
        Result denied=ReflectionTestUtils.invokeMethod(service,"reportDeliveryIssue",30L,10L,"未收到商品");
        assertThat(denied.getCode()).isNotEqualTo(200);
        Result result=ReflectionTestUtils.invokeMethod(service,"reportDeliveryIssue",20L,10L,"未收到商品");
        assertThat(result.getCode()).isEqualTo(200);assertThat(order.getStatus()).isEqualTo(6);
        assertThat(ReflectionTestUtils.getField(order,"deliveryIssue")).isEqualTo("未收到商品");
        Result remind=ReflectionTestUtils.invokeMethod(service,"remindBuyer",30L,10L);
        assertThat(remind.getCode()).isNotEqualTo(200);
        assertThat(service.confirmOrder(30L,10L).getCode()).isNotEqualTo(200);
        assertThat(service.confirmOrder(20L,10L).getCode()).isEqualTo(200);
        assertThat(order.getStatus()).isEqualTo(3);
        verify(productMapper,never()).updateById(any(Product.class));
    }

    @Test
    void peerOrderQueryRequiresAnotherUserAndAuthenticatedViewer() {
        Result anonymous = ReflectionTestUtils.invokeMethod(service, "getOrdersWithUser", null, 30L, 1, 10);
        Result self = ReflectionTestUtils.invokeMethod(service, "getOrdersWithUser", 20L, 20L, 1, 10);
        assertThat(anonymous.getCode()).isNotEqualTo(200);
        assertThat(self.getCode()).isNotEqualTo(200);
        verify(orderMapper, never()).selectPage(any(), any());
    }

    @Test
    void peerOrderQueryIncludesBothDirectionsAndRespectsViewerHiddenFlags() {
        when(orderMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Order> query = invocation.getArgument(1);
            String sql = query.getSqlSegment();
            assertThat(sql).contains("buyer_id", "seller_id", "buyer_deleted", "seller_deleted", " OR ");
            assertThat(sql).contains("CASE WHEN status IN (1,2,5,6) THEN 0 ELSE 1 END ASC", "create_time DESC", "id DESC");
            assertThat(query.getParamNameValuePairs().values()).contains(20L, 30L, 0).hasSize(6);
            IPage<Order> page = invocation.getArgument(0);
            assertThat(page.getCurrent()).isEqualTo(1);
            assertThat(page.getSize()).isEqualTo(50);
            page.setRecords(Collections.emptyList()); page.setTotal(0);
            return page;
        });
        Result result = ReflectionTestUtils.invokeMethod(service, "getOrdersWithUser", 20L, 30L, -1, 1000);
        assertThat(result.getCode()).isEqualTo(200);
        verify(orderMapper).selectPage(any(), any());
    }

    @Test
    void duplicatePendingAcceptanceOrderIsNotCreatedAgain() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
            new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), Order.class);
        Product product = product(40L, 30L, 1, new BigDecimal("99.00")); product.setStock(5);
        when(productMapper.selectById(40L)).thenReturn(product);
        when(orderMapper.selectCount(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<Order> query = invocation.getArgument(0);
            query.getSqlSegment();
            return query.getParamNameValuePairs().containsValue(5) ? 1L : 0L;
        });
        assertThat(service.createOrder(20L, 40L, 1, "Test address").getCode()).isNotEqualTo(200);
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    void acceptanceConflictDoesNotSendSuccessNotification() {
        Order order = completedOrder(10L, 20L, 30L); order.setStatus(5);
        when(orderMapper.selectById(10L)).thenReturn(order);
        when(orderMapper.update(any(), any())).thenReturn(0);
        assertThat(service.acceptOrder(30L, 10L).getCode()).isNotEqualTo(200);
        org.mockito.Mockito.verifyNoInteractions(notificationService);
    }

    @Test
    void newOrdersWaitForSellerAcceptanceBeforeDelivery() {
        Product product = product(40L, 30L, 1, new BigDecimal("99.00")); product.setStock(5);
        when(productMapper.selectById(40L)).thenReturn(product);
        assertThat(service.createOrder(20L, 40L, 1, "Test address").getCode()).isEqualTo(200);
        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert(saved.capture());
        Order order = saved.getValue(); order.setId(10L);
        assertThat(order.getStatus()).isEqualTo(5);
        when(orderMapper.selectById(10L)).thenReturn(order);
        assertThat(service.shipOrder(30L, 10L, null).getCode()).isNotEqualTo(200);
    }

    @Test
    void sellerAcceptsOnlyOwnWaitingOrderAndNotifiesBuyerOnce() {
        Order order = completedOrder(10L, 20L, 30L); order.setStatus(5);
        when(orderMapper.selectById(10L)).thenReturn(order);
        when(orderMapper.update(any(), any())).thenReturn(1);
        Result denied = ReflectionTestUtils.invokeMethod(service, "acceptOrder", 20L, 10L);
        assertThat(denied.getCode()).isNotEqualTo(200);
        Result accepted = ReflectionTestUtils.invokeMethod(service, "acceptOrder", 30L, 10L);
        assertThat(accepted.getCode()).isEqualTo(200);
        assertThat(order.getStatus()).isEqualTo(1);
        assertThat(ReflectionTestUtils.getField(order, "acceptTime")).isNotNull();
        Result repeated = ReflectionTestUtils.invokeMethod(service, "acceptOrder", 30L, 10L);
        assertThat(repeated.getCode()).isNotEqualTo(200);
        verify(notificationService).notifyUser(20L, "order", "卖家已接单", "卖家已接单，请通过站内聊天协商面交时间", 10L);
        verify(productMapper, never()).updateById(any(Product.class));
    }

    @Test
    void buyerCanCancelWaitingAcceptanceAndRestoreStock() {
        Order order = completedOrder(10L, 20L, 30L); order.setStatus(5); order.setProductId(40L); order.setQuantity(1);
        Product product = product(40L, 30L, 3, new BigDecimal("99.00")); product.setStock(0);
        when(orderMapper.selectById(10L)).thenReturn(order);
        when(productMapper.selectById(40L)).thenReturn(product);
        assertThat(service.cancelOrder(20L, 10L).getCode()).isEqualTo(200);
        assertThat(order.getStatus()).isEqualTo(4);
        assertThat(product.getStock()).isEqualTo(1);
    }

    @Test
    void sellerCanRejectBeforeAcceptance() {
        Order order = completedOrder(10L, 20L, 30L); order.setStatus(5); order.setProductId(40L); order.setQuantity(1);
        Product product = product(40L, 30L, 3, new BigDecimal("99.00")); product.setStock(0);
        when(orderMapper.selectById(10L)).thenReturn(order);
        when(productMapper.selectById(40L)).thenReturn(product);
        assertThat(service.rejectOrder(30L, 10L, "无法按约交易").getCode()).isEqualTo(200);
        assertThat(order.getStatus()).isEqualTo(4);
        verify(notificationService).notifyUser(org.mockito.ArgumentMatchers.eq(20L), org.mockito.ArgumentMatchers.eq("order"),
                org.mockito.ArgumentMatchers.eq("卖家拒绝接单"), org.mockito.ArgumentMatchers.contains("无法按约交易"), org.mockito.ArgumentMatchers.eq(10L));
    }

    @Test
    void newOrderKeepsProductSnapshotEvenWhenProductChanges() {
        Product product = product(40L, 30L, 1, new BigDecimal("99.00"));
        product.setTitle("下单时的教材");
        product.setStock(5);
        com.secondhand.entity.ProductImage image = new com.secondhand.entity.ProductImage();
        image.setProductId(40L); image.setUrl("/original.jpg");
        when(productImageMapper.selectByProductIds(any())).thenReturn(java.util.List.of(image));
        when(productMapper.selectById(40L)).thenReturn(product);
        assertThat(service.createOrder(20L, 40L, 2, "Test address").getCode()).isEqualTo(200);
        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert(saved.capture());
        Order order = saved.getValue(); order.setId(10L);
        product.setTitle("后来修改的商品"); product.setPrice(new BigDecimal("199.00"));
        when(orderMapper.selectById(10L)).thenReturn(order);
        when(productMapper.selectBatchIds(any())).thenReturn(java.util.List.of(product));
        Result detail = service.getOrderDetail(20L, 10L);
        assertThat(detail.getCode()).isEqualTo(200);
        Order result = (Order) detail.getData();
        assertThat(ReflectionTestUtils.getField(result, "snapshotTitle")).isEqualTo("下单时的教材");
        assertThat(ReflectionTestUtils.getField(result, "snapshotImage")).isEqualTo("/original.jpg");
        assertThat(ReflectionTestUtils.getField(result, "unitPrice")).isEqualTo(new BigDecimal("99.00"));
        assertThat(result.getProductTitle()).isEqualTo("下单时的教材");
    }

    @Test
    void notificationOrderIsVisibleOnlyToItsParticipants() {
        Order order = completedOrder(10L, 20L, 30L);
        when(orderMapper.selectById(10L)).thenReturn(order);
        Result denied = ReflectionTestUtils.invokeMethod(service, "getOrderDetail", 99L, 10L);
        assertThat(denied.getCode()).isEqualTo(500);
        Result allowed = ReflectionTestUtils.invokeMethod(service, "getOrderDetail", 30L, 10L);
        assertThat(allowed.getCode()).isEqualTo(200);
        assertThat(allowed.getData()).isSameAs(order);
    }

    @Test
    void orderKeepsConfirmedProductLocationAfterProductChanges() {
        Product product = product(40L, 30L, 1, new BigDecimal("99.00"));
        product.setStock(5);
        product.setTradeLocation(" 学校东门 ");
        when(productMapper.selectById(40L)).thenReturn(product);
        Result result = service.createOrder(20L, 40L, 1, "学校东门");
        assertThat(result.getCode()).isEqualTo(200);
        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert(saved.capture());
        product.setTradeLocation("图书馆");
        assertThat(saved.getValue().getAddress()).isEqualTo("学校东门");
    }

    @Test
    void cannotOrderProductWithoutLocation() {
        Product product = product(40L, 30L, 1, new BigDecimal("99.00"));
        product.setStock(5);
        product.setTradeLocation(" ");
        when(productMapper.selectById(40L)).thenReturn(product);
        assertThat(service.createOrder(20L, 40L, 1, "任意地点").getCode()).isEqualTo(500);
        verify(orderMapper, never()).insert(any(Order.class));
        verify(productMapper, never()).updateById(any(Product.class));
    }

    @Test
    void cannotOrderWhenConfirmedLocationHasChanged() {
        Product product = product(40L, 30L, 1, new BigDecimal("99.00"));
        product.setStock(5);
        product.setTradeLocation("图书馆");
        when(productMapper.selectById(40L)).thenReturn(product);
        assertThat(service.createOrder(20L, 40L, 1, "学校东门").getCode()).isEqualTo(500);
        verify(orderMapper, never()).insert(any(Order.class));
        verify(productMapper, never()).updateById(any(Product.class));
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
        assertThat(ReflectionTestUtils.getField(order, "cancelTime")).isNotNull();

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
        assertThat(ReflectionTestUtils.getField(updatedOrder, "cancelTime")).isNotNull();
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
    void batchMeetupDeliverySucceedsWithoutTrackingNumber() {
        Order order = completedOrder(10L, 20L, 30L);
        order.setStatus(1);
        when(orderMapper.selectById(10L)).thenReturn(order);

        OrderBatchShipItem item = new OrderBatchShipItem();
        item.setOrderId(10L);
        item.setExpressNo(" ");

        Result result = service.batchShipOrders(30L, Collections.singletonList(item));

        assertThat(result.getCode()).isEqualTo(200);
        BatchOperationResult data = (BatchOperationResult) result.getData();
        assertThat(data.getSuccessCount()).isEqualTo(1);
        assertThat(data.getFailCount()).isEqualTo(0);
        assertThat(order.getStatus()).isEqualTo(2);
    }

    @Test
    void meetupDeliveryRequiresSellerAndPendingStateButNoTrackingNumber() {
        Order order = completedOrder(10L, 20L, 30L);
        order.setStatus(1);
        when(orderMapper.selectById(10L)).thenReturn(order);
        assertThat(service.shipOrder(99L, 10L, null).getCode()).isNotEqualTo(200);
        assertThat(order.getStatus()).isEqualTo(1);
        assertThat(service.confirmOrder(20L, 10L).getCode()).isNotEqualTo(200);
        assertThat(service.shipOrder(30L, 10L, null).getCode()).isEqualTo(200);
        assertThat(order.getStatus()).isEqualTo(2);
        assertThat(order.getExpressNo()).isNull();
        assertThat(service.shipOrder(30L, 10L, null).getCode()).isNotEqualTo(200);
        assertThat(service.confirmOrder(99L, 10L).getCode()).isNotEqualTo(200);
        assertThat(service.confirmOrder(20L, 10L).getCode()).isEqualTo(200);
        assertThat(order.getStatus()).isEqualTo(3);
        assertThat(service.confirmOrder(20L, 10L).getCode()).isNotEqualTo(200);
    }

    @Test
    void orderListsReloadEvaluatedFlagFromDatabaseWithoutTrustingOldObjectState() {
        // 纯Mockito测试未启动MyBatis，需初始化select(lambda)使用的实体字段映射。
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), Evaluation.class);
        EvaluationMapper evaluations = mock(EvaluationMapper.class);
        ReflectionTestUtils.setField(service, "evaluationMapper", evaluations);
        Order order = completedOrder(10L, 20L, 30L);
        when(orderMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            IPage<Order> page = invocation.getArgument(0);
            page.setRecords(Collections.singletonList(order));
            page.setTotal(1);
            return page;
        });
        when(productMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());
        when(productImageMapper.selectByProductIds(any())).thenReturn(Collections.emptyList());
        when(userMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());
        Evaluation evaluation = new Evaluation();
        evaluation.setOrderId(10L); evaluation.setFromUserId(20L);
        when(evaluations.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.singletonList(evaluation));
        service.getMyBoughtOrders(20L, 1, 12);
        assertThat(ReflectionTestUtils.getField(order, "evaluated")).isEqualTo(true);
        service.getMySoldOrders(30L, 1, 12);
        assertThat(ReflectionTestUtils.getField(order, "evaluated")).isEqualTo(true);
        when(evaluations.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        service.getMyBoughtOrders(20L, 1, 12);
        assertThat(ReflectionTestUtils.getField(order, "evaluated")).isEqualTo(false);
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
        product.setTradeLocation("Test address");
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
        order.setMeetupTime(new java.util.Date(1));
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
