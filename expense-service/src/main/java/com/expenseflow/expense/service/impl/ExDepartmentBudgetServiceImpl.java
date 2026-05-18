package com.expenseflow.expense.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.expenseflow.common.exception.BusinessException;
import com.expenseflow.expense.entity.ExDepartmentBudget;
import com.expenseflow.expense.mapper.ExDepartmentBudgetMapper;
import com.expenseflow.expense.service.ExDepartmentBudgetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Year;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExDepartmentBudgetServiceImpl extends ServiceImpl<ExDepartmentBudgetMapper, ExDepartmentBudget>
        implements ExDepartmentBudgetService {

    @Override
    @Transactional
    public void deductBudget(Long departmentId, BigDecimal amount, Long tenantId) {
        ExDepartmentBudget budget = getCurrentBudget(departmentId, tenantId);
        if (budget == null) {
            log.warn("部门(id={})无预算配置，跳过预算校验", departmentId);
            return;
        }
        BigDecimal remaining = budget.getTotalAmount().subtract(budget.getUsedAmount());
        if (remaining.compareTo(amount) < 0) {
            throw new BusinessException(String.format(
                "部门预算不足：需 %.2f 元，剩余 %.2f 元", amount, remaining));
        }
        budget.setUsedAmount(budget.getUsedAmount().add(amount));
        updateById(budget);

        BigDecimal ratio = budget.getUsedAmount().divide(budget.getTotalAmount(), 4, BigDecimal.ROUND_HALF_UP);
        if (ratio.compareTo(budget.getAlertThreshold()) >= 0) {
            log.warn("部门(id={})预算使用率已达 {}%，触发预警",
                departmentId, ratio.multiply(BigDecimal.valueOf(100)).intValue());
        }
    }

    @Override
    public ExDepartmentBudget getCurrentBudget(Long departmentId, Long tenantId) {
        int year = Year.now().getValue();
        LambdaQueryWrapper<ExDepartmentBudget> qw = new LambdaQueryWrapper<>();
        qw.eq(ExDepartmentBudget::getDepartmentId, departmentId)
          .eq(ExDepartmentBudget::getBudgetYear, year)
          .eq(ExDepartmentBudget::getTenantId, tenantId)
          .eq(ExDepartmentBudget::getStatus, "ACTIVE")
          .isNull(ExDepartmentBudget::getBudgetQuarter)
          .last("LIMIT 1");
        return getOne(qw);
    }
}
