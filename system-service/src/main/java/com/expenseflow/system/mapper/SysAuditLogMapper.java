package com.expenseflow.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.expenseflow.system.entity.SysAuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysAuditLogMapper extends BaseMapper<SysAuditLog> {
}
