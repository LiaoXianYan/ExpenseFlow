package com.expenseflow.expense.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.expenseflow.expense.entity.ExDepartmentBudget;

public interface ExDepartmentBudgetService extends IService<ExDepartmentBudget> {
    void deductBudget(Long departmentId, java.math.BigDecimal amount, Long tenantId);
    ExDepartmentBudget getCurrentBudget(Long departmentId, Long tenantId);
}
