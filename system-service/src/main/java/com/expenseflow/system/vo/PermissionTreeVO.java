package com.expenseflow.system.vo;

import lombok.Data;
import java.util.List;

@Data
public class PermissionTreeVO {
    private Long id;
    private Long parentId;
    private String permissionCode;
    private String permissionName;
    private Integer permissionType;
    private String path;
    private String icon;
    private Integer sortOrder;
    private List<PermissionTreeVO> children;
}
