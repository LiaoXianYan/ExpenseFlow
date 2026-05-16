package com.expenseflow.notification.controller;

import com.expenseflow.notification.BaseControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MessageControllerTest extends BaseControllerTest {

    @Test
    @DisplayName("GET /notification/message/unread-count 返回 200 和未读数")
    void shouldReturnUnreadCount() throws Exception {
        getWithJwt("/notification/message/unread-count", 1L)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data").isNumber());
    }

    @Test
    @DisplayName("GET /notification/message/page 返回分页结果")
    void shouldReturnPage() throws Exception {
        getWithJwt("/notification/message/page?page=1&size=10", 1L)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.records").isArray())
            .andExpect(jsonPath("$.data.total").isNumber());
    }
}
