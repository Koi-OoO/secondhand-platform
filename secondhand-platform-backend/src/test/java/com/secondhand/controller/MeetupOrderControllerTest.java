package com.secondhand.controller;

import com.secondhand.entity.User;
import com.secondhand.service.OrderService;
import com.secondhand.util.Result;
import com.secondhand.util.SessionUtil;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MeetupOrderControllerTest {
    @Test void chatOrdersCannotOverrideSessionViewerWithRequestParameter() throws Exception {
        OrderService service = mock(OrderService.class);
        when(service.getOrdersWithUser(20L, 30L, 2, 10)).thenReturn(Result.success(java.util.Collections.emptyList()));
        OrderController controller = new OrderController();
        ReflectionTestUtils.setField(controller, "orderService", service);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/order/with-user/30"))
                .andExpect(jsonPath("$.code").value(500));
        verifyNoInteractions(service);
        MockHttpSession session = new MockHttpSession();
        User buyer = new User(); buyer.setId(20L); SessionUtil.login(session, buyer);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/order/with-user/30")
                .session(session).param("page", "2").param("userId", "99"))
                .andExpect(jsonPath("$.code").value(200));
        verify(service).getOrdersWithUser(20L, 30L, 2, 10);
    }

    @Test void acceptRouteUsesSessionAndRejectsAnonymousCalls() throws Exception {
        OrderService service = mock(OrderService.class);
        when(service.acceptOrder(30L, 10L)).thenReturn(Result.success("接单成功"));
        OrderController controller = new OrderController();
        ReflectionTestUtils.setField(controller, "orderService", service);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(put("/order/10/accept")).andExpect(jsonPath("$.code").value(500));
        verifyNoInteractions(service);
        MockHttpSession session = new MockHttpSession();
        User seller = new User(); seller.setId(30L); SessionUtil.login(session, seller);
        mvc.perform(put("/order/10/accept").session(session).param("sellerId", "99"))
                .andExpect(jsonPath("$.code").value(200));
        verify(service).acceptOrder(30L, 10L);
    }

    @Test void meetupRoutesAcceptDeliveryWithoutExpressNumberAndUseSessionSeller() throws Exception {
        OrderService service = mock(OrderService.class);
        when(service.shipOrder(30L, 10L, null)).thenReturn(Result.success("交付成功"));
        when(service.batchShipOrders(eq(30L), anyList())).thenReturn(Result.success("批量交付成功"));
        OrderController controller = new OrderController();
        ReflectionTestUtils.setField(controller, "orderService", service);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        MockHttpSession session = new MockHttpSession();
        User seller = new User(); seller.setId(30L); SessionUtil.login(session, seller);
        mvc.perform(put("/order/10/deliver").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mvc.perform(put("/order/batch/deliver").session(session).contentType("application/json")
                .content("{\"items\":[{\"orderId\":10}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(service).shipOrder(30L, 10L, null);
        verify(service).batchShipOrders(eq(30L), argThat(items -> items.size() == 1 && items.get(0).getOrderId().equals(10L)));
    }
}
