package com.expenseflow.expense.controller;

import com.expenseflow.common.annotation.AuditLog;
import com.expenseflow.common.result.Result;
import com.expenseflow.expense.entity.ExPaymentRecord;
import com.expenseflow.expense.service.PaymentService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/expense/payment")
@RequiredArgsConstructor
public class PaymentRecordController extends BaseController {

    private final PaymentService paymentService;

    @PreAuthorize("hasAuthority('payment:view')")
    @GetMapping("/page")
    public Result<Page<ExPaymentRecord>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return paymentService.page(page, size);
    }

    @PreAuthorize("hasAuthority('payment:create')")
    @PostMapping("/pay")
    @AuditLog(module = "打款管理", operation = "PAY")
    public Result<ExPaymentRecord> pay(@RequestParam Long reportId) {
        return paymentService.pay(reportId, getCurrentUserId());
    }

    @PreAuthorize("hasAuthority('payment:create')")
    @PostMapping("/{reportId}/pay")
    @AuditLog(module = "打款管理", operation = "PAY")
    public Result<ExPaymentRecord> payByPath(@PathVariable Long reportId) {
        return paymentService.pay(reportId, getCurrentUserId());
    }
}
