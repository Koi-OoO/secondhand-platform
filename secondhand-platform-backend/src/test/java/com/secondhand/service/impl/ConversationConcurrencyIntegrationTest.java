package com.secondhand.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.secondhand.entity.*;
import com.secondhand.mapper.*;
import com.secondhand.service.MessageService;
import com.secondhand.util.Result;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/** 显式启用的本机 MySQL 并发验收；独立临时用户、真实提交，finally 清理，不发真实推送。 */
@EnabledIfSystemProperty(named="conversation.flow.integration", matches="true")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes=RealOrderFlowIntegrationTest.Config.class)
class ConversationConcurrencyIntegrationTest {
    @Autowired UserMapper users;
    @Autowired MessageMapper messages;
    @Autowired ConversationMapper conversations;
    @Autowired MessageService service;
    @Autowired PlatformTransactionManager manager;

    @RepeatedTest(3)
    void simultaneousFirstOpenAndMessagesShareOneConversationAndNeverRegressSummary() throws Exception {
        TransactionTemplate tx=new TransactionTemplate(manager);
        String token=UUID.randomUUID().toString().substring(0,8);
        User first=user("conv_it_"+token+"_a"),second=user("conv_it_"+token+"_b");
        tx.execute(status->{users.insert(first);users.insert(second);return null;});
        long a=first.getId(), b=second.getId();
        ExecutorService pool=Executors.newFixedThreadPool(8);
        try {
            CyclicBarrier start=new CyclicBarrier(8);
            List<Future<Result>> jobs=new ArrayList<>();
            for(int i=0;i<8;i++) {
                final int index=i;
                jobs.add(pool.submit(()->{
                    start.await(10,TimeUnit.SECONDS);
                    if(index%2==0) return service.createOrGetConversation(a,b,null);
                    return service.send(b,a,"并发首条消息-"+index,null);
                }));
            }
            for(Future<Result> job:jobs) assertThat(job.get(30,TimeUnit.SECONDS).getCode()).isEqualTo(200);
            tx.execute(status->{
                assertThat(conversations.selectCount(pair(a,b))).isEqualTo(1);
                List<Message> stored=messages.selectList(new QueryWrapper<Message>().eq("from_user_id",b).eq("to_user_id",a));
                assertThat(stored).hasSize(4);
                long maxId=stored.stream().mapToLong(Message::getId).max().orElseThrow();
                Conversation current=conversations.selectByPair(Math.min(a,b),Math.max(a,b));
                assertThat(current.getLastMessageId()).isEqualTo(maxId);
                Date lastTime=current.getLastTime();
                Long conversationId=current.getId();

                // 最后一次打开空会话不能抹掉消息摘要。
                service.createOrGetConversation(a,b,null);
                Conversation late=new Conversation();
                late.setUser1Id(Math.min(a,b));late.setUser2Id(Math.max(a,b));
                late.setLastMessageId(stored.stream().mapToLong(Message::getId).min().orElseThrow());
                late.setLastTime(new Date(0));late.setCreateTime(new Date());late.setUpdateTime(new Date());
                conversations.upsertAfterMessage(late);
                Conversation after=conversations.selectByPair(Math.min(a,b),Math.max(a,b));
                assertThat(after.getId()).isEqualTo(conversationId);
                assertThat(after.getLastMessageId()).isEqualTo(maxId);
                assertThat(after.getLastTime()).isEqualTo(lastTime);
                return null;
            });
        } finally {
            pool.shutdownNow();
            if(!pool.awaitTermination(35,TimeUnit.SECONDS)) throw new IllegalStateException("并发任务未结束，保留测试记录以便排查");
            tx.execute(status->{
                messages.delete(new QueryWrapper<Message>().in("from_user_id",a,b).in("to_user_id",a,b));
                conversations.delete(pair(a,b));
                users.deleteById(a);users.deleteById(b);
                return null;
            });
        }
    }
    private QueryWrapper<Conversation> pair(long a,long b) {
        return new QueryWrapper<Conversation>().eq("user1_id",Math.min(a,b)).eq("user2_id",Math.max(a,b));
    }
    private User user(String name) {
        User user=new User();user.setUsername(name);user.setPassword("temporary-integration-only");user.setStatus(1);return user;
    }
}
