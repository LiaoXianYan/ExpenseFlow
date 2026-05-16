package com.expenseflow.expense.controller;

import com.expenseflow.expense.BaseControllerTest;
import com.expenseflow.expense.dto.ExpenseReportDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ExpenseReportControllerTest extends BaseControllerTest {

    @Test
    @DisplayName("GET /expense/report/page → 200 + JSON 分页结构")
    void shouldReturnPagedReports() throws Exception {
        getWithJwt("/expense/report/page?page=1&size=10", 1L, "USER")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    @DisplayName("POST /expense/report → 创建草稿报销单")
    void shouldCreateDraftReport() throws Exception {
        ExpenseReportDTO dto = new ExpenseReportDTO();
        dto.setReportDate(LocalDate.now());
        dto.setRemark("测试报销单");

        postWithJwt("/expense/report", dto, 1L, "USER")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.reportNo").isNotEmpty());
    }
}
