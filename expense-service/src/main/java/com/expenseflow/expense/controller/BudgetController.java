package com.expenseflow.expense.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.expenseflow.common.result.Result;
import com.expenseflow.expense.entity.ExDepartmentBudget;
import com.expenseflow.expense.service.ExDepartmentBudgetService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/expense/budget")
@RequiredArgsConstructor
public class BudgetController {

    private final ExDepartmentBudgetService budgetService;

    @GetMapping("/page")
    @PreAuthorize("@pms.hasPermission('finance:budget')")
    public Result<Page<ExDepartmentBudget>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Integer budgetYear) {
        LambdaQueryWrapper<ExDepartmentBudget> qw = new LambdaQueryWrapper<>();
        if (departmentId != null) qw.eq(ExDepartmentBudget::getDepartmentId, departmentId);
        if (budgetYear != null) qw.eq(ExDepartmentBudget::getBudgetYear, budgetYear);
        qw.orderByDesc(ExDepartmentBudget::getBudgetYear);
        return Result.ok(budgetService.page(new Page<>(page, size), qw));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@pms.hasPermission('finance:budget')")
    public Result<ExDepartmentBudget> getById(@PathVariable Long id) {
        return Result.ok(budgetService.getById(id));
    }

    @PostMapping
    @PreAuthorize("@pms.hasPermission('finance:budget')")
    public Result<Void> create(@RequestBody ExDepartmentBudget budget) {
        budget.setUsedAmount(budget.getUsedAmount() != null ? budget.getUsedAmount() : BigDecimal.ZERO);
        budget.setAlertThreshold(budget.getAlertThreshold() != null ? budget.getAlertThreshold() : new BigDecimal("0.80"));
        budget.setStatus("ACTIVE");
        budgetService.save(budget);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("@pms.hasPermission('finance:budget')")
    public Result<Void> update(@PathVariable Long id, @RequestBody ExDepartmentBudget budget) {
        budget.setId(id);
        budgetService.updateById(budget);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@pms.hasPermission('finance:budget')")
    public Result<Void> delete(@PathVariable Long id) {
        budgetService.removeById(id);
        return Result.ok();
    }
}
