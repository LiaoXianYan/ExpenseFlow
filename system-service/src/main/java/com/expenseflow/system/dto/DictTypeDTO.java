package com.expenseflow.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DictTypeDTO {
    @NotBlank(message = "字典编码不能为空")
    private String dictCode;
    @NotBlank(message = "字典名称不能为空")
    private String dictName;
}
