package com.expenseflow.expense.controller;

import com.expenseflow.common.annotation.AuditLog;
import com.expenseflow.common.result.Result;
import com.expenseflow.expense.entity.ExPaymentRecord;
import com.expenseflow.expense.service.PaymentService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/expense/payment")
@RequiredArgsConstructor
public class PaymentRecordController {

    private final PaymentService paymentService;

    @GetMapping("/page")
    public Result<Page<ExPaymentRecord>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return paymentService.page(page, size);
    }

    @PostMapping("/pay")
    @AuditLog(module = "打款管理", operation = "PAY")
    public Result<ExPaymentRecord> pay(@RequestParam Long reportId, @RequestHeader("X-User-Id") Long operatorId) {
        return paymentService.pay(reportId, operatorId);
    }
}
