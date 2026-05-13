package com.expenseflow.system.entity;

import com.expenseflow.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
public class SysPermission extends BaseEntity {
    private Long parentId;
    private String permissionCode;
    private String permissionName;
    private Integer permissionType;
    private String path;
    private String icon;
    private Integer sortOrder;
}
