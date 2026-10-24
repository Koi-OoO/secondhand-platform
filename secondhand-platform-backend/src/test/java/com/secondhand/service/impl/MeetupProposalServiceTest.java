package com.secondhand.service.impl;

import com.secondhand.entity.*;
import com.secondhand.mapper.*;
import com.secondhand.service.MessageService;
import com.secondhand.service.NotificationService;
import com.secondhand.util.Result;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MeetupProposalServiceTest {
    private MeetupProposalService service;
    private OrderMapper orders;
    private MeetupProposalMapper proposals;
    private NotificationService notifications;
    private Order order;
    private MeetupProposal proposal;
    @BeforeEach void setup() {
        orders=mock(OrderMapper.class); proposals=mock(MeetupProposalMapper.class);
        notifications=mock(NotificationService.class); service=new MeetupProposalService();
        ReflectionTestUtils.setField(service,"orders",orders);
        ReflectionTestUtils.setField(service,"proposals",proposals);
        ReflectionTestUtils.setField(service,"notifications",notifications);
        ReflectionTestUtils.setField(service,"messages",mock(MessageService.class));
        order=new Order();order.setId(10L);order.setSellerId(30L);order.setBuyerId(20L);order.setStatus(1);
        order.setMeetupTime(new Date(System.currentTimeMillis()+100000));
        when(orders.selectOne(any())).thenReturn(order);
        when(orders.selectById(10L)).thenReturn(order);
        when(orders.update(any(),any())).thenReturn(1);
        proposal=new MeetupProposal();proposal.setId(1L);proposal.setOrderId(10L);proposal.setSellerId(30L);proposal.setBuyerId(20L);
        proposal.setStatus("pending");proposal.setProposedTime(new Date(System.currentTimeMillis()+200000));
        when(proposals.selectById(1L)).thenReturn(proposal);
        when(proposals.selectOne(any())).thenReturn(proposal);
    }
    @Test void onlySellerCanProposeForAcceptedOrderAndFutureTime() {
        assertThat(service.propose(20L,10L,proposal.getProposedTime()).getCode()).isNotEqualTo(200);
        assertThat(service.propose(30L,10L,new Date(0)).getCode()).isNotEqualTo(200);
        order.setStatus(5);
        assertThat(service.propose(30L,10L,proposal.getProposedTime()).getCode()).isNotEqualTo(200);
        verify(proposals,never()).insert(any(MeetupProposal.class));
    }
    @Test void newProposalExpiresPendingOnesButDoesNotChangeConfirmedTime() {
        Date previous=order.getMeetupTime();
        assertThat(service.propose(30L,10L,proposal.getProposedTime()).getCode()).isEqualTo(200);
        verify(proposals).update(isNull(),any());
        verify(proposals).insert(any(MeetupProposal.class));
        verify(orders,never()).update(any(),any());
        assertThat(order.getMeetupTime()).isEqualTo(previous);
    }
    @Test void buyerConfirmationChangesTimeAndRejectsDuplicate() {
        assertThat(service.respond(20L,1L,true).getCode()).isEqualTo(200);
        assertThat(order.getMeetupTime()).isEqualTo(proposal.getProposedTime());
        assertThat(proposal.getStatus()).isEqualTo("confirmed");
        assertThat(service.respond(20L,1L,true).getCode()).isNotEqualTo(200);
        verify(notifications,times(1)).notifyUser(eq(30L),eq("order"),anyString(),anyString(),eq(10L));
    }
    @Test void onlyBuyerCanRespondAndDecliningKeepsPreviousAgreement() {
        Date previous=order.getMeetupTime();
        assertThat(service.respond(30L,1L,true).getCode()).isNotEqualTo(200);
        assertThat(service.respond(99L,1L,true).getCode()).isNotEqualTo(200);
        assertThat(service.respond(20L,1L,false).getCode()).isEqualTo(200);
        assertThat(proposal.getStatus()).isEqualTo("declined");
        assertThat(order.getMeetupTime()).isEqualTo(previous);
        verify(orders,never()).update(any(),any());
    }
    @Test void expiredReplacedOrClosedOrderCannotBeConfirmed() {
        proposal.setStatus("expired");
        assertThat(service.respond(20L,1L,true).getCode()).isNotEqualTo(200);
        proposal.setStatus("pending");proposal.setProposedTime(new Date(0));
        assertThat(service.respond(20L,1L,true).getCode()).isNotEqualTo(200);
        proposal.setProposedTime(new Date(System.currentTimeMillis()+200000));order.setStatus(2);
        assertThat(service.respond(20L,1L,true).getCode()).isNotEqualTo(200);
        verify(orders,never()).update(any(),any());
    }
}
