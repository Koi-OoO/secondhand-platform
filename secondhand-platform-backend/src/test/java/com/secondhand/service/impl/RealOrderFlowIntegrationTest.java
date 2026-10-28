package com.secondhand.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import com.secondhand.entity.*;
import com.secondhand.mapper.*;
import com.secondhand.service.*;
import com.secondhand.util.Result;
import com.secondhand.websocket.ChatWebSocketHandler;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.redisson.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 显式启用的本地MySQL事务测试；所有测试写入最终回滚，不发真实消息。 */
@EnabledIfSystemProperty(named="order.flow.integration", matches="true")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes=RealOrderFlowIntegrationTest.Config.class)
@Transactional
class RealOrderFlowIntegrationTest {
 // 仅由 @ContextConfiguration 显式加载；不加 @Configuration，避免被其他 Spring Boot 测试组件扫描。
 @EnableTransactionManagement @MapperScan("com.secondhand.mapper")
 @Import({OrderServiceImpl.class,MeetupProposalService.class,MessageServiceImpl.class,EvaluationServiceImpl.class})
 static class Config {
  @Bean DataSource dataSource(org.springframework.core.env.Environment environment) throws Exception {
   YamlPropertiesFactoryBean yaml=new YamlPropertiesFactoryBean();yaml.setResources(new ClassPathResource("application.yml"));
   Properties props=yaml.getObject();
   ClassPathResource local=new ClassPathResource("application-local.yml");
   if(local.exists()) {
    // 直接读取本地覆盖项，不把本地敏感配置交给会打印内容的配置调试日志。
    try(var input=local.getInputStream()) {
     java.util.Map<String,Object> data=new org.yaml.snakeyaml.Yaml().load(input);
     Object spring=data.get("spring");
     if(spring instanceof java.util.Map) {
      Object datasource=((java.util.Map<?,?>)spring).get("datasource");
      if(datasource instanceof java.util.Map) for(String key:java.util.List.of("url","username","password")) {
       Object value=((java.util.Map<?,?>)datasource).get(key);
       if(value!=null) props.setProperty("spring.datasource."+key,value.toString());
      }
     }
    }
   }
   String url=environment.resolveRequiredPlaceholders(props.getProperty("spring.datasource.url"));
   if(!url.startsWith("jdbc:mysql://localhost:") && !url.startsWith("jdbc:mysql://127.0.0.1:")) throw new IllegalStateException("Only local test database allowed");
   return new DriverManagerDataSource(url,environment.resolveRequiredPlaceholders(props.getProperty("spring.datasource.username")),environment.resolveRequiredPlaceholders(props.getProperty("spring.datasource.password")));
  }
  @Bean PlatformTransactionManager transactionManager(DataSource ds){return new DataSourceTransactionManager(ds);}
  @Bean SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
   MybatisSqlSessionFactoryBean factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(ds);
   com.baomidou.mybatisplus.core.MybatisConfiguration cfg=new com.baomidou.mybatisplus.core.MybatisConfiguration();cfg.setMapUnderscoreToCamelCase(true);factory.setConfiguration(cfg);
   factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*.xml"));
   MybatisPlusInterceptor plugin=new MybatisPlusInterceptor();plugin.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));factory.setPlugins(plugin);
   return factory.getObject();
  }
  @Bean RedisTemplate<String,Object> redisTemplate(){return mock(RedisTemplate.class);}
  @Bean RedissonClient redissonClient() throws Exception {
   RedissonClient client=mock(RedissonClient.class);RLock lock=mock(RLock.class);
   when(client.getLock(anyString())).thenReturn(lock);when(lock.tryLock(anyLong(),anyLong(),any(TimeUnit.class))).thenReturn(true);when(lock.isHeldByCurrentThread()).thenReturn(true);return client;
  }
  @Bean NotificationService notificationService(){return mock(NotificationService.class);}
  @Bean ChatWebSocketHandler chatWebSocketHandler(){return mock(ChatWebSocketHandler.class);}
  @Bean com.secondhand.websocket.ProductChangePublisher productChangePublisher(){return mock(com.secondhand.websocket.ProductChangePublisher.class);}
 }
 @Autowired OrderService orders;
 @Autowired MeetupProposalService appointments;
 @Autowired EvaluationService evaluations;
 @Autowired OrderMapper orderMapper;
 @Autowired ProductMapper productMapper;
 @Autowired UserMapper userMapper;
 @Autowired CategoryMapper categoryMapper;
 @Autowired MeetupProposalMapper proposalMapper;
 @Autowired MessageMapper messageMapper;
 @Test void actualMysqlFlowRejectsSkippedStepsAndPreservesAgreementAndIssueHistory() {
  List<User> users=userMapper.selectList(new QueryWrapper<User>().eq("status",1).last("LIMIT 2"));
  assertThat(users).hasSize(2);Long buyer=users.get(0).getId(),seller=users.get(1).getId();
  Product product=new Product();product.setSellerId(seller);product.setTitle("事务回滚验收商品");product.setDescription("仅测试，事务结束后回滚");product.setPrice(new BigDecimal("19.00"));product.setStock(2);product.setStatus(1);product.setConditionLevel(2);product.setTradeLocation("测试面交地点");product.setCategoryId(categoryMapper.selectList(new QueryWrapper<Category>().last("LIMIT 1")).get(0).getId());productMapper.insert(product);
  assertThat(orders.createOrder(buyer,product.getId(),1,product.getTradeLocation()).getCode()).isEqualTo(200);
  Order order=orderMapper.selectOne(new QueryWrapper<Order>().eq("product_id",product.getId()));Long id=order.getId();
  assertThat(order.getStatus()).isEqualTo(5);
  assertThat(orders.createOrder(buyer,product.getId(),1,product.getTradeLocation()).getCode()).isNotEqualTo(200);
  assertThat(orders.shipOrder(seller,id,null).getCode()).isNotEqualTo(200);
  assertThat(orders.confirmOrder(buyer,id).getCode()).isNotEqualTo(200);
  assertThat(orders.acceptOrder(buyer,id).getCode()).isNotEqualTo(200);
  assertThat(orders.acceptOrder(seller,id).getCode()).isEqualTo(200);
  assertThat(orders.shipOrder(seller,id,null).getCode()).isNotEqualTo(200);
  Date time=new Date(((System.currentTimeMillis()+86400000)/1000)*1000);
  assertThat(appointments.propose(buyer,id,time).getCode()).isNotEqualTo(200);
  MeetupProposal first=(MeetupProposal)appointments.propose(seller,id,time).getData();
  assertThat(orders.shipOrder(seller,id,null).getCode()).isNotEqualTo(200);
  assertThat(appointments.respond(buyer,first.getId(),false).getCode()).isEqualTo(200);
  assertThat(orderMapper.selectById(id).getMeetupTime()).isNull();
  MeetupProposal replaced=(MeetupProposal)appointments.propose(seller,id,time).getData();
  MeetupProposal active=(MeetupProposal)appointments.propose(seller,id,time).getData();
  assertThat(appointments.respond(buyer,replaced.getId(),true).getCode()).isNotEqualTo(200);
  assertThat(appointments.respond(seller,active.getId(),true).getCode()).isNotEqualTo(200);
  assertThat(appointments.respond(buyer,active.getId(),true).getCode()).isEqualTo(200);
  assertThat(appointments.respond(buyer,active.getId(),true).getCode()).isNotEqualTo(200);
  MeetupProposal reschedule=(MeetupProposal)appointments.propose(seller,id,new Date(time.getTime()+3600000)).getData();
  assertThat(orderMapper.selectById(id).getMeetupTime()).isEqualTo(time);
  assertThat(orders.shipOrder(seller,id,null).getCode()).isEqualTo(200);
  assertThat(appointments.respond(buyer,reschedule.getId(),true).getCode()).isNotEqualTo(200);
  assertThat(orders.remindBuyer(seller,id).getCode()).isEqualTo(200);
  assertThat(orders.remindBuyer(seller,id).getCode()).isNotEqualTo(200);
  assertThat(orders.reportDeliveryIssue(seller,id,"未收到").getCode()).isNotEqualTo(200);
  assertThat(orders.reportDeliveryIssue(buyer,id,"尚未收到商品，请协商").getCode()).isEqualTo(200);
  assertThat(orderMapper.selectById(id).getStatus()).isEqualTo(6);
  assertThat(orders.remindBuyer(seller,id).getCode()).isNotEqualTo(200);
  assertThat(orders.confirmOrder(seller,id).getCode()).isNotEqualTo(200);
  assertThat(orders.confirmOrder(buyer,id).getCode()).isEqualTo(200);
  assertThat(orderMapper.selectById(id).getDeliveryIssue()).isEqualTo("尚未收到商品，请协商");
  assertThat(orders.confirmOrder(buyer,id).getCode()).isNotEqualTo(200);
  assertThat(evaluations.evaluate(seller,id,5,"越权",false).getCode()).isNotEqualTo(200);
  assertThat(evaluations.evaluate(buyer,id,5,"x".repeat(501),false).getCode()).isNotEqualTo(200);
  assertThat(evaluations.evaluate(buyer,id,5,"问题解决后完成",false).getCode()).isEqualTo(200);
  assertThat(evaluations.evaluate(buyer,id,5,"重复",false).getCode()).isNotEqualTo(200);
  assertThat(messageMapper.selectCount(new QueryWrapper<Message>().eq("product_id",product.getId()).isNotNull("meetup_proposal_id"))).isEqualTo(4L);
  assertThat(productMapper.selectById(product.getId()).getStock()).isEqualTo(1);
  assertThat(orders.createOrder(buyer,product.getId(),1,product.getTradeLocation()).getCode()).isEqualTo(200);
  Order pending=orderMapper.selectOne(new QueryWrapper<Order>().eq("product_id",product.getId()).eq("status",5));
  assertThat(orders.cancelOrder(seller,pending.getId()).getCode()).isNotEqualTo(200);
  assertThat(orders.cancelOrder(buyer,pending.getId()).getCode()).isEqualTo(200);
  assertThat(orders.cancelOrder(buyer,pending.getId()).getCode()).isNotEqualTo(200);
  assertThat(productMapper.selectById(product.getId()).getStock()).isEqualTo(1);
  assertThat(orders.createOrder(buyer,product.getId(),1,product.getTradeLocation()).getCode()).isEqualTo(200);
  Order next=orderMapper.selectOne(new QueryWrapper<Order>().eq("product_id",product.getId()).eq("status",5));
  assertThat(orders.acceptOrder(seller,next.getId()).getCode()).isEqualTo(200);
  MeetupProposal cancelled=(MeetupProposal)appointments.propose(seller,next.getId(),time).getData();
  assertThat(orders.rejectOrder(seller,next.getId(),"无法面交").getCode()).isEqualTo(200);
  assertThat(appointments.respond(buyer,cancelled.getId(),true).getCode()).isNotEqualTo(200);
  assertThat(productMapper.selectById(product.getId()).getStock()).isEqualTo(1);

 }
}
