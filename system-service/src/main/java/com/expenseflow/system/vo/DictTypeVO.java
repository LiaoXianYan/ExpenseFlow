package com.expenseflow.system.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DictTypeVO {
    private Long id;
    private Long tenantId;
    private String dictCode;
    private String dictName;
    private Integer status;
    private LocalDateTime createTime;
}
