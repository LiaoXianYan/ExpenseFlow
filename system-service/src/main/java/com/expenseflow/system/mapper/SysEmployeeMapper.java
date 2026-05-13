package com.expenseflow.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.expenseflow.system.entity.SysEmployee;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysEmployeeMapper extends BaseMapper<SysEmployee> {
}
