package com.expenseflow.expense.service;

import com.expenseflow.common.result.Result;
import com.expenseflow.expense.entity.ExExpenseReport;
import com.expenseflow.expense.entity.ExPaymentRecord;
import com.expenseflow.expense.mapper.ExExpenseReportMapper;
import com.expenseflow.expense.mapper.ExPaymentRecordMapper;
import com.expenseflow.expense.util.NoGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.expenseflow.common.handler.ExpenseFlowTenantLineHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final ExPaymentRecordMapper paymentMapper;
    private final ExExpenseReportMapper reportMapper;
    private final NoGenerator noGenerator;
    private final RabbitTemplate rabbitTemplate;
    private final ExDepartmentBudgetService budgetService;

    public Result<Page<ExPaymentRecord>> page(int page, int size) {
        LambdaQueryWrapper<ExPaymentRecord> qw = new LambdaQueryWrapper<>();
        qw.orderByDesc(ExPaymentRecord::getCreateTime);
        return Result.ok(paymentMapper.selectPage(new Page<>(page, size), qw));
    }

    @Transactional
    public Result<ExPaymentRecord> pay(Long reportId, Long operatorId) {
        ExExpenseReport report = reportMapper.selectById(reportId);
        if (report == null) return Result.fail(404, "报销单不存在");
        if (!"APPROVED".equals(report.getStatus()))
            return Result.fail(400, "仅已审批的报销单可打款");

        // 扣减部门预算
        try {
            Long deptId = report.getDepartmentId();
            if (deptId != null && report.getTotalAmount() != null) {
                Long tenantId = report.getTenantId();
                if (tenantId == null) tenantId = ExpenseFlowTenantLineHandler.getTenant();
                budgetService.deductBudget(deptId, report.getTotalAmount(), tenantId);
            }
        } catch (Exception e) {
            log.warn("预算扣减失败: {}", e.getMessage());
        }

        ExPaymentRecord pr = new ExPaymentRecord();
        pr.setReportId(reportId);
        pr.setPaymentNo(noGenerator.generatePaymentNo());
        pr.setPayeeName("报销人");
        pr.setPayeeAccount("未填写");
        pr.setAmount(report.getTotalAmount());
        pr.setPaymentMethod("BANK_TRANSFER");
        pr.setPaymentStatus("SUCCESS");
        pr.setPaymentTime(LocalDateTime.now());
        pr.setOperatorId(operatorId);
        paymentMapper.insert(pr);

        report.setStatus("PAID");
        report.setPaidAmount(report.getTotalAmount());
        report.setPaidTime(LocalDateTime.now());
        reportMapper.updateById(report);

        // 发布打款事件
        Map<String, Object> event = new java.util.HashMap<>();
        event.put("eventId", java.util.UUID.randomUUID().toString());
        event.put("eventType", "PAYMENT_COMPLETED");
        event.put("reportNo", report.getReportNo());
        event.put("amount", report.getTotalAmount().toString());
        event.put("paidTime", pr.getPaymentTime().toString());
        event.put("tenantId", 0);
        try {
            rabbitTemplate.convertAndSend("expense.exchange", "expense.payment.completed", event);
        } catch (Exception ignored) {}

        return Result.ok(pr);
    }
}
