package com.expenseflow.system.entity;

import com.expenseflow.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_oauth_user")
public class SysOauthUser extends BaseEntity {
    private Long userId;
    private String provider;
    private String openId;
    private String unionId;
    private String accessToken;
    private String refreshToken;
}
