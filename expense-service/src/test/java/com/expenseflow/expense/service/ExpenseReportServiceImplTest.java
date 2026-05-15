package com.expenseflow.expense.service;

import com.expenseflow.common.result.Result;
import com.expenseflow.expense.dto.ExpenseItemDTO;
import com.expenseflow.expense.dto.ExpenseReportDTO;
import com.expenseflow.expense.entity.ExExpenseReport;
import com.expenseflow.expense.feign.ApprovalFeignClient;
import com.expenseflow.expense.feign.SystemFeignClient;
import com.expenseflow.expense.mapper.ExExpenseReportMapper;
import com.expenseflow.expense.mapper.ExExpenseItemMapper;
import com.expenseflow.expense.mapper.ExTravelRequestMapper;
import com.expenseflow.expense.service.impl.ExpenseReportServiceImpl;
import com.expenseflow.expense.util.NoGenerator;
import com.expenseflow.expense.util.PolicyValidator;
import com.expenseflow.expense.vo.ExpenseReportVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseReportServiceImplTest {

    @Mock ExExpenseReportMapper reportMapper;
    @Mock ExExpenseItemMapper itemMapper;
    @Mock ExTravelRequestMapper travelMapper;
    @Mock SystemFeignClient systemFeignClient;
    @Mock ApprovalFeignClient approvalFeignClient;
    @Mock NoGenerator noGenerator;
    @Mock PolicyValidator policyValidator;
    @Mock RabbitTemplate rabbitTemplate;
    @InjectMocks ExpenseReportServiceImpl reportService;

    @Test
    @DisplayName("getById: 存在返回 VO")
    void shouldReturnWhenFound() {
        ExExpenseReport r = new ExExpenseReport();
        r.setId(1L); r.setStatus("DRAFT"); r.setTotalAmount(BigDecimal.ZERO);
        when(reportMapper.selectById(1L)).thenReturn(r);
        when(itemMapper.selectList(any())).thenReturn(java.util.Collections.emptyList());

        Result<ExpenseReportVO> result = reportService.getById(1L);
        assertThat(result.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("getById: 不存在返回 404")
    void shouldReturn404WhenNotFound() {
        when(reportMapper.selectById(999L)).thenReturn(null);
        Result<ExpenseReportVO> result = reportService.getById(999L);
        assertThat(result.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("create: 关联不存在的出差申请返回 400")
    void shouldFailWhenTravelRequestMissing() {
        ExpenseReportDTO dto = new ExpenseReportDTO();
        dto.setTravelRequestId(999L);
        when(noGenerator.generateReportNo()).thenReturn("ER-001");
        when(travelMapper.selectById(999L)).thenReturn(null);

        Result<ExpenseReportVO> result = reportService.create(dto);
        assertThat(result.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("create: 成功创建返回 DRAFT")
    void shouldCreateDraftSuccessfully() {
        ExpenseReportDTO dto = new ExpenseReportDTO();
        when(noGenerator.generateReportNo()).thenReturn("ER-001");
        when(reportMapper.insert(any(ExExpenseReport.class))).thenReturn(1);

        Result<ExpenseReportVO> result = reportService.create(dto);
        assertThat(result.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("submit: 仅 DRAFT 状态可提交")
    void shouldSubmitOnlyWhenDraft() {
        ExExpenseReport r = new ExExpenseReport();
        r.setId(1L); r.setStatus("APPROVED");
        when(reportMapper.selectById(1L)).thenReturn(r);

        Result<ExpenseReportVO> result = reportService.submit(1L);
        assertThat(result.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("submit: 无明细返回 400")
    void shouldFailWhenNoItems() {
        ExExpenseReport r = new ExExpenseReport();
        r.setId(1L); r.setStatus("DRAFT"); r.setApplicantId(1L);
        r.setTotalAmount(BigDecimal.ZERO);
        when(reportMapper.selectById(1L)).thenReturn(r);
        when(itemMapper.selectCount(any())).thenReturn(0L);

        Result<ExpenseReportVO> result = reportService.submit(1L);
        assertThat(result.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("withdraw: 非 DRAFT/REJECTED/WITHDRAWN 可撤回")
    void shouldWithdrawWhenApproved() {
        ExExpenseReport r = new ExExpenseReport();
        r.setId(1L); r.setStatus("APPROVED");
        when(reportMapper.selectById(1L)).thenReturn(r);
        when(reportMapper.updateById(any(ExExpenseReport.class))).thenReturn(1);

        Result<ExpenseReportVO> result = reportService.withdraw(1L);
        assertThat(result.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("update: 非 DRAFT 不可编辑")
    void shouldNotUpdateWhenNotDraft() {
        ExExpenseReport r = new ExExpenseReport();
        r.setId(1L); r.setStatus("APPROVING");
        when(reportMapper.selectById(1L)).thenReturn(r);

        Result<ExpenseReportVO> result = reportService.update(1L, new ExpenseReportDTO());
        assertThat(result.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("delete: 非 DRAFT 不可删除")
    void shouldNotDeleteWhenNotDraft() {
        ExExpenseReport r = new ExExpenseReport();
        r.setId(1L); r.setStatus("APPROVING");
        when(reportMapper.selectById(1L)).thenReturn(r);

        Result<Void> result = reportService.delete(1L);
        assertThat(result.getCode()).isEqualTo(400);
    }
}
