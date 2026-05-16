package com.expenseflow.approval.controller;

import com.expenseflow.approval.BaseControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApprovalTaskControllerTest extends BaseControllerTest {

    @Test
    @DisplayName("GET /approval/task/record/list -> 审批记录列表")
    void shouldReturnApprovalRecords() throws Exception {
        getWithJwt("/approval/task/record/list?businessType=EXPENSE_REPORT&businessId=1", 1L, "APPROVER")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /approval/task/page -> 待办任务列表")
    void shouldReturnTaskList() throws Exception {
        getWithJwt("/approval/task/page?candidateGroup=manager", 1L, "APPROVER")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }
}
