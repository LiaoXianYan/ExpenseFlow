package com.expenseflow.system.service;

import com.expenseflow.common.result.Result;
import com.expenseflow.common.util.JwtUtil;
import com.expenseflow.system.dto.LoginDTO;
import com.expenseflow.system.entity.SysUser;
import com.expenseflow.system.mapper.SysUserMapper;
import com.expenseflow.system.mapper.SysUserRoleMapper;
import com.expenseflow.system.mapper.SysRoleMapper;
import com.expenseflow.system.service.impl.AuthServiceImpl;
import com.expenseflow.system.vo.TokenVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ValueOperations<String, Object> valueOps;
    @Mock SysUserMapper userMapper;
    @Mock SysUserRoleMapper userRoleMapper;
    @Mock SysRoleMapper roleMapper;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks AuthServiceImpl authService;

    private LoginDTO loginDto(String username, String password) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        return dto;
    }

    private void stubRedis() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("登录成功应返回 Token")
    void shouldReturnTokenWhenCredentialsValid() {
        stubRedis();
        SysUser user = new SysUser();
        user.setId(1L); user.setUsername("admin"); user.setPassword("hashed");
        user.setTenantId(0L); user.setStatus(1);

        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("admin123", "hashed")).thenReturn(true);
        when(userRoleMapper.selectList(any())).thenReturn(java.util.Collections.emptyList());

        Result<TokenVO> result = authService.login(loginDto("admin", "admin123"));
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getAccessToken()).isNotBlank();
    }

    @Test
    @DisplayName("密码错误应返回 401")
    void shouldFailWhenPasswordWrong() {
        SysUser user = new SysUser();
        user.setId(1L); user.setPassword("hashed"); user.setStatus(1);

        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        Result<TokenVO> result = authService.login(loginDto("admin", "wrong"));
        assertThat(result.getCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("用户被禁用应返回 403")
    void shouldFailWhenUserDisabled() {
        SysUser user = new SysUser();
        user.setId(1L); user.setPassword("hashed"); user.setStatus(0);

        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("admin123", "hashed")).thenReturn(true);

        Result<TokenVO> result = authService.login(loginDto("admin", "admin123"));
        assertThat(result.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("用户不存在应返回 401")
    void shouldFailWhenUserNotFound() {
        when(userMapper.selectOne(any())).thenReturn(null);

        Result<TokenVO> result = authService.login(loginDto("nobody", "any"));
        assertThat(result.getCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("logout 应将 token 写入黑名单")
    void shouldBlacklistTokenOnLogout() {
        stubRedis();
        String token = JwtUtil.generateAccessToken(1L, 0L, "test-token-id",
            java.util.List.of("SUPER_ADMIN"), "admin");

        Result<Void> result = authService.logout(token);
        assertThat(result.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("refresh: valid refreshToken 返回新 Token")
    void shouldRefreshTokenWhenValid() {
        stubRedis();
        String oldRefresh = JwtUtil.generateRefreshToken(1L, 0L, "old-token-id",
            java.util.List.of("SUPER_ADMIN"), "admin");

        SysUser user = new SysUser();
        user.setId(1L); user.setUsername("admin"); user.setTenantId(0L);
        when(userRoleMapper.selectList(any())).thenReturn(java.util.Collections.emptyList());
        when(userMapper.selectById(1L)).thenReturn(user);

        Result<TokenVO> result = authService.refresh(oldRefresh);
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getAccessToken()).isNotBlank();
    }

    @Test
    @DisplayName("refresh: 过期 token 返回 401")
    void shouldFailRefreshWhenExpired() {
        String expiredToken = "eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiJ0ZXN0Iiwic3ViIjoiMSIsInRlbmFudElkIjowLCJleHAiOjEwfQ.signature";

        Result<TokenVO> result = authService.refresh(expiredToken);
        assertThat(result.getCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("me: 存在用户返回 UserVO")
    void shouldReturnUserWhenExists() {
        SysUser user = new SysUser();
        user.setId(1L); user.setUsername("admin");

        when(userMapper.selectById(1L)).thenReturn(user);

        var result = authService.me(1L);
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isNotNull();
    }
}
