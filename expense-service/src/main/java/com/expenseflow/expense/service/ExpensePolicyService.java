package com.expenseflow.expense.service;

import com.expenseflow.common.result.Result;
import com.expenseflow.expense.dto.ExpensePolicyDTO;
import com.expenseflow.expense.entity.ExExpensePolicy;
import com.expenseflow.expense.mapper.ExExpensePolicyMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpensePolicyService {

    private final ExExpensePolicyMapper policyMapper;

    public Result<Page<ExExpensePolicy>> page(int page, int size, String keyword) {
        LambdaQueryWrapper<ExExpensePolicy> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            qw.like(ExExpensePolicy::getPolicyName, keyword);
        }
        qw.orderByDesc(ExExpensePolicy::getCreateTime);
        return Result.ok(policyMapper.selectPage(new Page<>(page, size), qw));
    }

    public Result<List<ExExpensePolicy>> list() {
        return Result.ok(policyMapper.selectList(
            new LambdaQueryWrapper<ExExpensePolicy>().orderByDesc(ExExpensePolicy::getCreateTime)));
    }

    public Result<ExExpensePolicy> getById(Long id) {
        ExExpensePolicy p = policyMapper.selectById(id);
        return p == null ? Result.fail(404, "费用政策不存在") : Result.ok(p);
    }

    @Transactional
    public Result<ExExpensePolicy> create(ExpensePolicyDTO dto) {
        ExExpensePolicy p = new ExExpensePolicy();
        BeanUtils.copyProperties(dto, p);
        p.setStatus(1);
        policyMapper.insert(p);
        return Result.ok(p);
    }

    @Transactional
    public Result<Void> update(Long id, ExpensePolicyDTO dto) {
        ExExpensePolicy p = policyMapper.selectById(id);
        if (p == null) return Result.fail(404, "费用政策不存在");
        BeanUtils.copyProperties(dto, p);
        p.setId(id);
        policyMapper.updateById(p);
        return Result.ok();
    }

    @Transactional
    public Result<Void> delete(Long id) {
        policyMapper.deleteById(id);
        return Result.ok();
    }
}
