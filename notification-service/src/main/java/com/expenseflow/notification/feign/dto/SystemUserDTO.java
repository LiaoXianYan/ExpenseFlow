package com.expenseflow.notification.feign.dto;

import lombok.Data;

@Data
public class SystemUserDTO {
    private Long id;
    private String username;
    private String realName;
    private String phone;
}
