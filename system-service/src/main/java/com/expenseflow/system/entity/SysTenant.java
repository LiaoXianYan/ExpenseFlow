package com.expenseflow.system.entity;

import com.expenseflow.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_tenant")
public class SysTenant extends BaseEntity {
    private String tenantCode;
    private String tenantName;
    private String contactName;
    private String contactPhone;
    private Integer status;
    private LocalDateTime expireTime;
}
