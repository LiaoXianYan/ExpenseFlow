package com.expenseflow.expense.controller;

import com.expenseflow.common.annotation.AuditLog;
import com.expenseflow.common.result.Result;
import com.expenseflow.expense.dto.ExpenseReportDTO;
import com.expenseflow.expense.service.ExpenseReportService;
import com.expenseflow.expense.vo.ExpenseReportVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/expense/report")
@RequiredArgsConstructor
public class ExpenseReportController {

    private final ExpenseReportService reportService;

    @GetMapping("/page")
    public Result<Page<ExpenseReportVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestHeader("X-User-Id") Long userId) {
        return reportService.page(page, size, userId, status);
    }

    @GetMapping("/{id}")
    public Result<ExpenseReportVO> getById(@PathVariable Long id) {
        return reportService.getById(id);
    }

    @PostMapping
    @AuditLog(module = "报销单", operation = "CREATE")
    public Result<ExpenseReportVO> create(@Valid @RequestBody ExpenseReportDTO dto) {
        return reportService.create(dto);
    }

    @PutMapping("/{id}")
    @AuditLog(module = "报销单", operation = "UPDATE")
    public Result<ExpenseReportVO> update(@PathVariable Long id, @Valid @RequestBody ExpenseReportDTO dto) {
        return reportService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @AuditLog(module = "报销单", operation = "DELETE")
    public Result<Void> delete(@PathVariable Long id) {
        return reportService.delete(id);
    }

    @PostMapping("/{id}/submit")
    @AuditLog(module = "报销单", operation = "SUBMIT")
    public Result<ExpenseReportVO> submit(@PathVariable Long id) {
        return reportService.submit(id);
    }

    @PostMapping("/{id}/withdraw")
    @AuditLog(module = "报销单", operation = "WITHDRAW")
    public Result<ExpenseReportVO> withdraw(@PathVariable Long id) {
        return reportService.withdraw(id);
    }
}
