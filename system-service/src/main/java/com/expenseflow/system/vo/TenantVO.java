package com.expenseflow.system.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TenantVO {
    private Long id;
    private String tenantCode;
    private String tenantName;
    private String contactName;
    private String contactPhone;
    private Integer status;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
}
