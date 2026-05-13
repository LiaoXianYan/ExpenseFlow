package com.expenseflow.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.expenseflow.system.entity.SysOauthUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysOauthUserMapper extends BaseMapper<SysOauthUser> {
}
