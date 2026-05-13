package com.expenseflow.system.controller;

import com.expenseflow.common.result.Result;
import com.expenseflow.system.dto.LoginDTO;
import com.expenseflow.system.service.AuthService;
import com.expenseflow.system.vo.TokenVO;
import com.expenseflow.system.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<TokenVO> login(@Valid @RequestBody LoginDTO dto) {
        return authService.login(dto);
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        return authService.logout(token);
    }

    @PostMapping("/refresh")
    public Result<TokenVO> refresh(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        return authService.refresh(token);
    }

    @GetMapping("/me")
    public Result<UserVO> me(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return authService.me(userId);
    }
}
