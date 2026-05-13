package com.expenseflow.expense.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.expenseflow.expense.entity.ExInvoice;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExInvoiceMapper extends BaseMapper<ExInvoice> {
}
