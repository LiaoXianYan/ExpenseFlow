package com.expenseflow.expense.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.expenseflow.expense.entity.ExExpenseItem;
import com.expenseflow.expense.entity.ExExpensePolicy;
import com.expenseflow.expense.mapper.ExExpensePolicyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;

@Component
@RequiredArgsConstructor
public class PolicyValidator {

    private final ExExpensePolicyMapper policyMapper;

    public List<String> validate(List<ExExpenseItem> items) {
        List<String> warnings = new ArrayList<>();
        List<ExExpensePolicy> policies = policyMapper.selectList(
            new LambdaQueryWrapper<ExExpensePolicy>().eq(ExExpensePolicy::getStatus, 1));
        Map<String, ExExpensePolicy> policyMap = new HashMap<>();
        for (ExExpensePolicy p : policies) {
            policyMap.put(p.getExpenseType(), p);
        }
        for (ExExpenseItem item : items) {
            ExExpensePolicy policy = policyMap.get(item.getExpenseType());
            if (policy == null) continue;
            if (item.getAmount().compareTo(policy.getMaxAmount()) > 0) {
                warnings.add(String.format("%s 超过单次上限 %s (实际: %s)",
                    item.getExpenseType(), policy.getMaxAmount(), item.getAmount()));
            }
        }
        return warnings;
    }
}
