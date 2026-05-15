package com.expenseflow.expense.service;

import com.expenseflow.common.result.Result;
import com.expenseflow.expense.dto.TravelRequestDTO;
import com.expenseflow.expense.entity.ExTravelRequest;
import com.expenseflow.expense.feign.ApprovalFeignClient;
import com.expenseflow.expense.feign.SystemFeignClient;
import com.expenseflow.expense.mapper.ExTravelRequestMapper;
import com.expenseflow.expense.service.impl.TravelRequestServiceImpl;
import com.expenseflow.expense.util.NoGenerator;
import com.expenseflow.expense.vo.TravelRequestVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TravelRequestServiceImplTest {

    @Mock ExTravelRequestMapper travelMapper;
    @Mock SystemFeignClient systemFeignClient;
    @Mock ApprovalFeignClient approvalFeignClient;
    @Mock NoGenerator noGenerator;
    @InjectMocks TravelRequestServiceImpl travelService;

    @Test
    @DisplayName("getById: 存在返回 VO")
    void shouldReturnWhenFound() {
        ExTravelRequest t = new ExTravelRequest();
        t.setId(1L); t.setStatus("DRAFT"); t.setEstimatedAmount(BigDecimal.ZERO);
        when(travelMapper.selectById(1L)).thenReturn(t);

        Result<TravelRequestVO> result = travelService.getById(1L);
        assertThat(result.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("getById: 不存在返回 404")
    void shouldReturn404WhenNotFound() {
        when(travelMapper.selectById(999L)).thenReturn(null);
        Result<TravelRequestVO> result = travelService.getById(999L);
        assertThat(result.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("create: 结束日期早于开始日期返回 400")
    void shouldFailWhenEndBeforeStart() {
        TravelRequestDTO dto = new TravelRequestDTO();
        dto.setStartDate(LocalDate.of(2026, 5, 20));
        dto.setEndDate(LocalDate.of(2026, 5, 15));

        Result<TravelRequestVO> result = travelService.create(dto);
        assertThat(result.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("create: 成功创建返回 DRAFT 状态")
    void shouldCreateDraftSuccessfully() {
        TravelRequestDTO dto = new TravelRequestDTO();
        dto.setStartDate(LocalDate.of(2026, 5, 10));
        dto.setEndDate(LocalDate.of(2026, 5, 20));
        dto.setEstimatedAmount(BigDecimal.valueOf(3000));

        when(noGenerator.generateTravelNo()).thenReturn("TR-20260515-0001");
        when(travelMapper.insert(any(ExTravelRequest.class))).thenReturn(1);

        Result<TravelRequestVO> result = travelService.create(dto);
        assertThat(result.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("submit: 仅 DRAFT 状态可提交")
    void shouldSubmitOnlyWhenDraft() {
        ExTravelRequest t = new ExTravelRequest();
        t.setId(1L); t.setStatus("APPROVED"); t.setEstimatedAmount(BigDecimal.valueOf(3000));
        when(travelMapper.selectById(1L)).thenReturn(t);

        Result<TravelRequestVO> result = travelService.submit(1L);
        assertThat(result.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("submit: DRAFT→APPROVING 成功")
    void shouldTransitionToApproving() {
        ExTravelRequest t = new ExTravelRequest();
        t.setId(1L); t.setStatus("DRAFT"); t.setEstimatedAmount(BigDecimal.valueOf(3000));
        t.setApplicantId(1L); t.setDepartmentId(1L); t.setRequestNo("TR-001");
        when(travelMapper.selectById(1L)).thenReturn(t);
        when(travelMapper.updateById(any(ExTravelRequest.class))).thenReturn(1);

        Result<TravelRequestVO> result = travelService.submit(1L);
        assertThat(result.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("withdraw: SUBMITTED/APPROVING/APPROVED 可撤回")
    void shouldWithdrawWhenApproved() {
        ExTravelRequest t = new ExTravelRequest();
        t.setId(1L); t.setStatus("APPROVED");
        when(travelMapper.selectById(1L)).thenReturn(t);
        when(travelMapper.updateById(any(ExTravelRequest.class))).thenReturn(1);

        Result<TravelRequestVO> result = travelService.withdraw(1L);
        assertThat(result.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("withdraw: DRAFT 状态不可撤回")
    void shouldNotWithdrawWhenDraft() {
        ExTravelRequest t = new ExTravelRequest();
        t.setId(1L); t.setStatus("DRAFT");
        when(travelMapper.selectById(1L)).thenReturn(t);

        Result<TravelRequestVO> result = travelService.withdraw(1L);
        assertThat(result.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("delete: 仅 DRAFT 可删除")
    void shouldDeleteOnlyWhenDraft() {
        ExTravelRequest t = new ExTravelRequest();
        t.setId(1L); t.setStatus("DRAFT");
        when(travelMapper.selectById(1L)).thenReturn(t);
        when(travelMapper.deleteById(1L)).thenReturn(1);

        Result<Void> result = travelService.delete(1L);
        assertThat(result.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("delete: 非 DRAFT 不可删除")
    void shouldNotDeleteWhenNotDraft() {
        ExTravelRequest t = new ExTravelRequest();
        t.setId(1L); t.setStatus("APPROVING");
        when(travelMapper.selectById(1L)).thenReturn(t);

        Result<Void> result = travelService.delete(1L);
        assertThat(result.getCode()).isEqualTo(400);
    }
}
