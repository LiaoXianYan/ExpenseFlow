package com.expenseflow.system.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DictDataVO {
    private Long id;
    private Long tenantId;
    private Long dictTypeId;
    private String dictLabel;
    private String dictValue;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createTime;
}
