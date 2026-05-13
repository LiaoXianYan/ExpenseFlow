package com.expenseflow.system.dto;

import lombok.Data;
import java.util.List;

@Data
public class RoleAssignUsersDTO {
    private List<Long> userIds;
}
