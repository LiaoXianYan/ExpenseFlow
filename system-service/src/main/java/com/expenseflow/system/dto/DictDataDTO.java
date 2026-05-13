package com.expenseflow.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DictDataDTO {
    @NotNull(message = "字典类型ID不能为空")
    private Long dictTypeId;
    @NotBlank(message = "标签不能为空")
    private String dictLabel;
    @NotBlank(message = "值不能为空")
    private String dictValue;
    private Integer sortOrder;
}
