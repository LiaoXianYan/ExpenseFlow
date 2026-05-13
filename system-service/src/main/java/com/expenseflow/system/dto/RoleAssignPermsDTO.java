package com.expenseflow.system.dto;

import lombok.Data;
import java.util.List;

@Data
public class RoleAssignPermsDTO {
    private List<Long> permissionIds;
}
