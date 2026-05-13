package com.expenseflow.expense.service;

import com.expenseflow.common.result.Result;
import com.expenseflow.expense.entity.ExExpenseReport;
import com.expenseflow.expense.entity.ExPaymentRecord;
import com.expenseflow.expense.mapper.ExExpenseReportMapper;
import com.expenseflow.expense.mapper.ExPaymentRecordMapper;
import com.expenseflow.expense.util.NoGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final ExPaymentRecordMapper paymentMapper;
    private final ExExpenseReportMapper reportMapper;
    private final NoGenerator noGenerator;

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

        ExPaymentRecord pr = new ExPaymentRecord();
        pr.setReportId(reportId);
        pr.setPaymentNo(noGenerator.generatePaymentNo());
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

        return Result.ok(pr);
    }
}
