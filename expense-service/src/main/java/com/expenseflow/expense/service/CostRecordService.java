package com.expenseflow.expense.service;

import com.expenseflow.common.result.Result;
import com.expenseflow.expense.dto.CostRecordDTO;
import com.expenseflow.expense.entity.ExCostRecord;
import com.expenseflow.expense.mapper.ExCostRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CostRecordService {

    private final ExCostRecordMapper costMapper;

    public Result<Page<ExCostRecord>> page(int page, int size, Long userId) {
        LambdaQueryWrapper<ExCostRecord> qw = new LambdaQueryWrapper<>();
        if (userId != null) qw.eq(ExCostRecord::getUserId, userId);
        qw.orderByDesc(ExCostRecord::getCreateTime);
        return Result.ok(costMapper.selectPage(new Page<>(page, size), qw));
    }

    @Transactional
    public Result<ExCostRecord> create(CostRecordDTO dto) {
        ExCostRecord r = new ExCostRecord();
        BeanUtils.copyProperties(dto, r);
        costMapper.insert(r);
        return Result.ok(r);
    }

    @Transactional
    public Result<Void> update(Long id, CostRecordDTO dto) {
        ExCostRecord r = costMapper.selectById(id);
        if (r == null) return Result.fail(404, "消费记录不存在");
        BeanUtils.copyProperties(dto, r);
        r.setId(id);
        costMapper.updateById(r);
        return Result.ok();
    }

    @Transactional
    public Result<Void> delete(Long id) {
        costMapper.deleteById(id);
        return Result.ok();
    }
}
