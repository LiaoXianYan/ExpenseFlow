package com.expenseflow.expense.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.expenseflow.expense.entity.ExPaymentRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExPaymentRecordMapper extends BaseMapper<ExPaymentRecord> {
}
