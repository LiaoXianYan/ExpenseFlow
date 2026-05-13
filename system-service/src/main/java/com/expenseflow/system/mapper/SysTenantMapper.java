package com.expenseflow.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.expenseflow.system.entity.SysTenant;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysTenantMapper extends BaseMapper<SysTenant> {
}
