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
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvaluationServiceImplTest {

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
    }

    @Test
    void anonymousEvaluationHidesReviewerIdentity() {
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
    }
}
