package com.secondhand.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.secondhand.entity.Evaluation;
import com.secondhand.entity.Order;
import com.secondhand.entity.Product;
import com.secondhand.entity.ProductImage;
import com.secondhand.entity.User;
import com.secondhand.mapper.EvaluationMapper;
import com.secondhand.mapper.OrderMapper;
import com.secondhand.mapper.ProductImageMapper;
import com.secondhand.mapper.ProductMapper;
import com.secondhand.mapper.UserMapper;
import com.secondhand.util.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvaluationServiceImplTest {

    @Test
    void submittingStarsAndTextDoesNotReadOrUpdateUserScore() {
        EvaluationMapper evaluations = mock(EvaluationMapper.class);
        OrderMapper orders = mock(OrderMapper.class);
        UserMapper users = mock(UserMapper.class);
        Order order = new Order(); order.setId(10L); order.setStatus(3);
        order.setBuyerId(20L); order.setSellerId(30L);
        when(orders.selectById(10L)).thenReturn(order);
        EvaluationServiceImpl service = new EvaluationServiceImpl();
        ReflectionTestUtils.setField(service, "evaluationMapper", evaluations);
        ReflectionTestUtils.setField(service, "orderMapper", orders);
        ReflectionTestUtils.setField(service, "userMapper", users);
        com.secondhand.service.NotificationService notifications = mock(com.secondhand.service.NotificationService.class);
        ReflectionTestUtils.setField(service, "notificationService", notifications);
        assertThat(service.evaluate(20L, 10L, 5, "物品符合描述", false).getCode()).isEqualTo(200);
        org.mockito.Mockito.verify(evaluations).insert(any(Evaluation.class));
        org.mockito.Mockito.verifyNoInteractions(users);
        org.mockito.Mockito.verify(notifications).notifyUser(30L, "order", "收到新评价",
                "买家已评价订单，请查看评价详情", 10L);
        when(evaluations.selectCount(any())).thenReturn(1L);
        assertThat(service.evaluate(20L, 10L, 5, "重复", false).getCode()).isEqualTo(500);
        org.mockito.Mockito.verifyNoMoreInteractions(notifications);
    }

    @Test
    void userEvaluationsIncludeProductContextFromOrders() {
        EvaluationMapper evaluationMapper = mock(EvaluationMapper.class);
        OrderMapper orderMapper = mock(OrderMapper.class);
        ProductMapper productMapper = mock(ProductMapper.class);
        ProductImageMapper productImageMapper = mock(ProductImageMapper.class);
        UserMapper userMapper = mock(UserMapper.class);

        Evaluation evaluation = new Evaluation();
        evaluation.setId(1L);
        evaluation.setOrderId(20L);
        evaluation.setFromUserId(30L);
        evaluation.setToUserId(40L);
        evaluation.setRating(5);
        evaluation.setContent("很好");

        Page<Evaluation> page = new Page<>(1, 12);
        page.setRecords(Collections.singletonList(evaluation));

        Order order = new Order();
        order.setId(20L);
        order.setProductId(10L);

        Product product = new Product();
        product.setId(10L);
        product.setTitle("iPhone 16 Pro");

        ProductImage productImage = new ProductImage();
        productImage.setProductId(10L);
        productImage.setUrl("https://example.com/product.png");

        User fromUser = new User();
        fromUser.setId(30L);
        fromUser.setUsername("buyer01");
        fromUser.setNickname("买家01");
        fromUser.setAvatar("https://example.com/avatar.png");

        when(evaluationMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
        when(userMapper.selectBatchIds(Collections.singletonList(30L))).thenReturn(Collections.singletonList(fromUser));
        when(orderMapper.selectBatchIds(Collections.singletonList(20L))).thenReturn(Collections.singletonList(order));
        when(productMapper.selectBatchIds(Collections.singletonList(10L))).thenReturn(Collections.singletonList(product));
        when(productImageMapper.selectByProductIds(Collections.singletonList(10L))).thenReturn(Collections.singletonList(productImage));

        EvaluationServiceImpl service = new EvaluationServiceImpl();
        ReflectionTestUtils.setField(service, "evaluationMapper", evaluationMapper);
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "productMapper", productMapper);
        ReflectionTestUtils.setField(service, "productImageMapper", productImageMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);

        Result result = service.getUserEvaluations(40L, 1, 12);

        assertThat(result.getCode()).isEqualTo(200);
        Page<Evaluation> data = (Page<Evaluation>) result.getData();
        Evaluation enriched = data.getRecords().get(0);
        assertThat(enriched.getFromUserNickname()).isEqualTo("买家01");
        assertThat(enriched.getFromUserAvatar()).isEqualTo("https://example.com/avatar.png");
        assertThat(enriched.getProductTitle()).isEqualTo("iPhone 16 Pro");
        assertThat(enriched.getProductImage()).isEqualTo("https://example.com/product.png");
        assertThat(enriched.getProductId()).isEqualTo(10L);
        assertThat(enriched.getFromUserId()).isEqualTo(30L);
        assertThat(enriched.getOrderId()).isNull();
        assertThat(evaluation.getOrderId()).isEqualTo(20L);
    }

    @Test
    void anonymousEvaluationHidesReviewerIdentity() throws Exception {
        EvaluationMapper evaluationMapper = mock(EvaluationMapper.class);
        OrderMapper orderMapper = mock(OrderMapper.class);
        ProductMapper productMapper = mock(ProductMapper.class);
        ProductImageMapper productImageMapper = mock(ProductImageMapper.class);
        UserMapper userMapper = mock(UserMapper.class);

        Evaluation evaluation = new Evaluation();
        evaluation.setId(2L);
        evaluation.setOrderId(21L);
        evaluation.setFromUserId(31L);
        evaluation.setToUserId(41L);
        evaluation.setRating(4);
        evaluation.setContent("匿名评价");
        evaluation.setAnonymous(true);

        Page<Evaluation> page = new Page<>(1, 12);
        page.setRecords(Collections.singletonList(evaluation));

        Order order = new Order();
        order.setId(21L);
        order.setProductId(11L);

        Product product = new Product();
        product.setId(11L);
        product.setTitle("匿名商品");

        User fromUser = new User();
        fromUser.setId(31L);
        fromUser.setUsername("private_buyer");
        fromUser.setNickname("真实买家");
        fromUser.setAvatar("https://example.com/private.png");

        when(evaluationMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
        when(userMapper.selectBatchIds(Collections.singletonList(31L))).thenReturn(Collections.singletonList(fromUser));
        when(orderMapper.selectBatchIds(Collections.singletonList(21L))).thenReturn(Collections.singletonList(order));
        when(productMapper.selectBatchIds(Collections.singletonList(11L))).thenReturn(Collections.singletonList(product));
        when(productImageMapper.selectByProductIds(Collections.singletonList(11L))).thenReturn(Collections.emptyList());

        EvaluationServiceImpl service = new EvaluationServiceImpl();
        ReflectionTestUtils.setField(service, "evaluationMapper", evaluationMapper);
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "productMapper", productMapper);
        ReflectionTestUtils.setField(service, "productImageMapper", productImageMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);

        Result result = service.getUserEvaluations(41L, 1, 12);

        Page<Evaluation> data = (Page<Evaluation>) result.getData();
        Evaluation enriched = data.getRecords().get(0);
        assertThat(enriched.getFromUserNickname()).isEqualTo("匿名用户");
        assertThat(enriched.getFromUserAvatar()).isNull();
        assertThat(enriched.getProductTitle()).isEqualTo("匿名商品");
        assertThat(enriched.getFromUserId()).isNull();
        assertThat(enriched.getOrderId()).isNull();
        // 只处理公开响应，不抹掉数据库实体上的真实身份与订单关联。
        assertThat(evaluation.getFromUserId()).isEqualTo(31L);
        assertThat(evaluation.getOrderId()).isEqualTo(21L);
        assertThat(enriched).isNotSameAs(evaluation);
        com.fasterxml.jackson.databind.JsonNode json = new ObjectMapper().valueToTree(result);
        com.fasterxml.jackson.databind.JsonNode record = json.path("data").path("records").get(0);
        assertThat(record.path("fromUserId").isNull()).isTrue();
        assertThat(record.path("orderId").isNull()).isTrue();
    }

    @Test
    void evaluationsKeepTitleAndImageFromPurchaseEvenAfterSellerChangesProduct() {
        SnapshotFixture fixture = new SnapshotFixture();
        fixture.order.setSnapshotTitle("下单时的商品名称");
        fixture.order.setSnapshotImage("/uploads/original.jpg");
        Evaluation view = fixture.query();
        assertThat(view.getProductTitle()).isEqualTo("下单时的商品名称");
        assertThat(view.getProductImage()).isEqualTo("/uploads/original.jpg");
    }

    @Test
    void newOrderWithoutCoverDoesNotAcquireImageAddedAfterPurchase() {
        SnapshotFixture fixture = new SnapshotFixture();
        fixture.order.setSnapshotTitle("下单时无封面");
        fixture.order.setSnapshotImage(null);
        assertThat(fixture.query().getProductImage()).isNull();
    }

    @Test
    void snapshotRemainsVisibleWhenCurrentProductIsMissing() {
        SnapshotFixture fixture = new SnapshotFixture();
        fixture.order.setSnapshotTitle("历史商品");
        fixture.order.setSnapshotImage("/uploads/original.jpg");
        when(fixture.products.selectBatchIds(any())).thenReturn(Collections.emptyList());
        when(fixture.images.selectByProductIds(any())).thenReturn(Collections.emptyList());
        Evaluation view = fixture.query();
        assertThat(view.getProductTitle()).isEqualTo("历史商品");
        assertThat(view.getProductImage()).isEqualTo("/uploads/original.jpg");
    }

    private static class SnapshotFixture {
        final ProductMapper products = mock(ProductMapper.class);
        final ProductImageMapper images = mock(ProductImageMapper.class);
        final Order order = new Order();
        final EvaluationServiceImpl service = new EvaluationServiceImpl();

        SnapshotFixture() {
            EvaluationMapper evaluations = mock(EvaluationMapper.class);
            OrderMapper orders = mock(OrderMapper.class);
            UserMapper users = mock(UserMapper.class);
            Evaluation evaluation = new Evaluation();
            evaluation.setOrderId(20L);
            evaluation.setFromUserId(30L);
            Page<Evaluation> page = new Page<>(1, 12);
            page.setRecords(Collections.singletonList(evaluation));
            order.setId(20L); order.setProductId(10L);
            Product product = new Product();
            product.setId(10L); product.setTitle("卖家后来修改的名称");
            ProductImage image = new ProductImage();
            image.setProductId(10L); image.setUrl("/uploads/later.jpg");
            when(evaluations.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
            when(orders.selectBatchIds(any())).thenReturn(Collections.singletonList(order));
            when(users.selectBatchIds(any())).thenReturn(Collections.emptyList());
            when(products.selectBatchIds(any())).thenReturn(Collections.singletonList(product));
            when(images.selectByProductIds(any())).thenReturn(Collections.singletonList(image));
            ReflectionTestUtils.setField(service, "evaluationMapper", evaluations);
            ReflectionTestUtils.setField(service, "orderMapper", orders);
            ReflectionTestUtils.setField(service, "productMapper", products);
            ReflectionTestUtils.setField(service, "productImageMapper", images);
            ReflectionTestUtils.setField(service, "userMapper", users);
        }

        Evaluation query() {
            Page<Evaluation> page = (Page<Evaluation>) service.getUserEvaluations(40L, 1, 12).getData();
            return page.getRecords().get(0);
        }
    }
}
