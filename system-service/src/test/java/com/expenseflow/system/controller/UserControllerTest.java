package com.expenseflow.system.controller;

import com.expenseflow.system.BaseControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserControllerTest extends BaseControllerTest {

    @Test
    @DisplayName("GET /system/role/list — SUPER_ADMIN 可获取角色列表")
    void shouldReturnRoleListForSuperAdmin() throws Exception {
        getWithJwt("/system/role/list", 1L, "SUPER_ADMIN")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$.data[0].roleCode").exists())
            .andExpect(jsonPath("$.data[0].roleName").exists());
    }

    @Test
    @DisplayName("GET /system/user/page — SUPER_ADMIN 可获取分页用户列表")
    void shouldReturnPaginatedUsersForSuperAdmin() throws Exception {
        getWithJwt("/system/user/page?page=1&size=10", 1L, "SUPER_ADMIN")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.records", hasSize(greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.data.records[0].username").exists())
            .andExpect(jsonPath("$.data.records[0].realName").exists());
    }
}
