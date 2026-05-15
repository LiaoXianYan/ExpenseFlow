package com.expenseflow.expense.controller;

import com.expenseflow.common.annotation.AuditLog;
import com.expenseflow.common.result.Result;
import com.expenseflow.expense.dto.ExpensePolicyDTO;
import com.expenseflow.expense.entity.ExExpensePolicy;
import com.expenseflow.expense.service.ExpensePolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/expense/policy")
@RequiredArgsConstructor
public class ExpensePolicyController {

    private final ExpensePolicyService policyService;

    @GetMapping("/list")
    public Result<List<ExExpensePolicy>> list() {
        return policyService.list();
    }

    @PostMapping
    @AuditLog(module = "费用政策", operation = "CREATE")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE')")
    public Result<ExExpensePolicy> create(@Valid @RequestBody ExpensePolicyDTO dto) {
        return policyService.create(dto);
    }

    @PutMapping("/{id}")
    @AuditLog(module = "费用政策", operation = "UPDATE")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ExpensePolicyDTO dto) {
        return policyService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @AuditLog(module = "费用政策", operation = "DELETE")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE')")
    public Result<Void> delete(@PathVariable Long id) {
        return policyService.delete(id);
    }
}
