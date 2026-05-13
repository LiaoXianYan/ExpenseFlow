package com.expenseflow.expense.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.expenseflow.expense.entity.ExExpenseItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExExpenseItemMapper extends BaseMapper<ExExpenseItem> {
}
