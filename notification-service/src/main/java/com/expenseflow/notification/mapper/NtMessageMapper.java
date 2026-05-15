package com.expenseflow.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.expenseflow.notification.entity.NtMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NtMessageMapper extends BaseMapper<NtMessage> {
}
