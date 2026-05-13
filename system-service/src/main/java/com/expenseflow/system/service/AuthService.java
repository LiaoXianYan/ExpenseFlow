package com.expenseflow.system.service;

import com.expenseflow.common.result.Result;
import com.expenseflow.system.dto.LoginDTO;
import com.expenseflow.system.vo.TokenVO;
import com.expenseflow.system.vo.UserVO;

public interface AuthService {
    Result<TokenVO> login(LoginDTO dto);

    Result<Void> logout(String token);

    Result<TokenVO> refresh(String refreshToken);

    Result<UserVO> me(Long userId);
}
