package com.expenseflow.system.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RoleVO {
    private Long id;
    private Long tenantId;
    private String roleCode;
    private String roleName;
    private Integer roleType;
    private Integer status;
    private LocalDateTime createTime;
}
