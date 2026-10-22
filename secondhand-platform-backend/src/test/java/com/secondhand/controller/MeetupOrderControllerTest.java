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
