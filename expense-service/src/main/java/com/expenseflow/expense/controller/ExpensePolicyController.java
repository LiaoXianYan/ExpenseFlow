package com.expenseflow.expense.controller;

import com.expenseflow.common.annotation.AuditLog;
import com.expenseflow.common.result.Result;
import com.expenseflow.expense.dto.ExpensePolicyDTO;
import com.expenseflow.expense.entity.ExExpensePolicy;
import com.expenseflow.expense.service.ExpensePolicyService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

    @PreAuthorize("hasAuthority('policy:view')")
    @GetMapping("/page")
    public Result<Page<ExExpensePolicy>> page(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(required = false) String keyword) {
        return policyService.page(page, size, keyword);
    }

    @PreAuthorize("hasAuthority('policy:view')")
    @GetMapping("/list")
    public Result<List<ExExpensePolicy>> list() {
        return policyService.list();
    }

    @PreAuthorize("hasAuthority('policy:view')")
    @GetMapping("/{id}")
    public Result<ExExpensePolicy> getById(@PathVariable Long id) {
        return policyService.getById(id);
    }

    @PreAuthorize("hasAuthority('policy:create')")
    @PostMapping
    @AuditLog(module = "费用政策", operation = "CREATE")
    public Result<ExExpensePolicy> create(@Valid @RequestBody ExpensePolicyDTO dto) {
        return policyService.create(dto);
    }

    @PreAuthorize("hasAuthority('policy:edit')")
    @PutMapping("/{id}")
    @AuditLog(module = "费用政策", operation = "UPDATE")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ExpensePolicyDTO dto) {
        return policyService.update(id, dto);
    }

    @PreAuthorize("hasAuthority('policy:delete')")
    @DeleteMapping("/{id}")
    @AuditLog(module = "费用政策", operation = "DELETE")
    public Result<Void> delete(@PathVariable Long id) {
        return policyService.delete(id);
    }
}
