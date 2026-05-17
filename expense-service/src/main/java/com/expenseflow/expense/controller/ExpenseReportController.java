package com.expenseflow.expense.controller;

import com.expenseflow.common.annotation.AuditLog;
import com.expenseflow.common.result.Result;
import com.expenseflow.expense.dto.ApplicantHistoryDTO;
import com.expenseflow.expense.dto.ExpenseReportDTO;
import com.expenseflow.expense.service.ExpenseReportService;
import com.expenseflow.expense.vo.ExpenseItemVO;
import com.expenseflow.expense.vo.ExpenseReportVO;
import com.expenseflow.expense.vo.InvoiceVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/expense/report")
@RequiredArgsConstructor
public class ExpenseReportController extends BaseController {

    private final ExpenseReportService reportService;

    @PreAuthorize("hasAuthority('report:view')")
    @GetMapping("/page")
    public Result<Page<ExpenseReportVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        return reportService.page(page, size, getCurrentUserId(), status);
    }

    @PreAuthorize("hasAuthority('report:view')")
    @GetMapping("/{id}")
    public Result<ExpenseReportVO> getById(@PathVariable Long id) {
        return reportService.getById(id);
    }

    @PreAuthorize("hasAuthority('report:create')")
    @PostMapping
    @AuditLog(module = "报销单", operation = "CREATE")
    public Result<ExpenseReportVO> create(@Valid @RequestBody ExpenseReportDTO dto) {
        dto.setApplicantId(getCurrentUserId());
        return reportService.create(dto);
    }

    @PreAuthorize("hasAuthority('report:edit')")
    @PutMapping("/{id}")
    @AuditLog(module = "报销单", operation = "UPDATE")
    public Result<ExpenseReportVO> update(@PathVariable Long id, @Valid @RequestBody ExpenseReportDTO dto) {
        return reportService.update(id, dto);
    }

    @PreAuthorize("hasAuthority('report:delete')")
    @DeleteMapping("/{id}")
    @AuditLog(module = "报销单", operation = "DELETE")
    public Result<Void> delete(@PathVariable Long id) {
        return reportService.delete(id);
    }

    @PreAuthorize("hasAuthority('report:submit')")
    @PostMapping("/{id}/submit")
    @AuditLog(module = "报销单", operation = "SUBMIT")
    public Result<ExpenseReportVO> submit(@PathVariable Long id) {
        return reportService.submit(id);
    }

    @PreAuthorize("hasAuthority('report:withdraw')")
    @PostMapping("/{id}/withdraw")
    @AuditLog(module = "报销单", operation = "WITHDRAW")
    public Result<ExpenseReportVO> withdraw(@PathVariable Long id) {
        return reportService.withdraw(id);
    }

    @GetMapping("/{id}/items")
    public Result<List<ExpenseItemVO>> getItems(@PathVariable Long id) {
        return reportService.getItemsByReportId(id);
    }

    @GetMapping("/{id}/invoices")
    public Result<List<InvoiceVO>> getInvoices(@PathVariable Long id) {
        return reportService.getInvoicesByReportId(id);
    }

    @GetMapping("/applicant/{applicantId}/history")
    public Result<ApplicantHistoryDTO> getApplicantHistory(@PathVariable Long applicantId) {
        return reportService.getApplicantHistory(applicantId);
    }
}
