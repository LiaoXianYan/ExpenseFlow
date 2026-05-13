package com.expenseflow.system.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TokenVO {
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private UserVO user;
}
