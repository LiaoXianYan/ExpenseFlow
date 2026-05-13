package com.expenseflow.system.vo;

import lombok.Data;
import java.util.List;

@Data
public class DeptTreeVO {
    private Long id;
    private Long parentId;
    private String deptName;
    private String deptCode;
    private Long leaderId;
    private Integer sortOrder;
    private List<DeptTreeVO> children;
}
