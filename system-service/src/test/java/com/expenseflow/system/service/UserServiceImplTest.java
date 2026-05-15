package com.expenseflow.system.service;

import com.expenseflow.common.result.Result;
import com.expenseflow.system.dto.UserDTO;
import com.expenseflow.system.entity.SysUser;
import com.expenseflow.system.mapper.SysUserMapper;
import com.expenseflow.system.service.impl.UserServiceImpl;
import com.expenseflow.system.vo.UserVO;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock SysUserMapper userMapper;
    @Mock PasswordEncoder passwordEncoder;
    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ValueOperations<String, Object> valueOps;
    @InjectMocks UserServiceImpl userService;

    private void stubRedis() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("getById: 存在用户返回 UserVO")
    void shouldReturnUserWhenFound() {
        SysUser user = new SysUser();
        user.setId(1L); user.setUsername("admin");
        when(userMapper.selectById(1L)).thenReturn(user);

        Result<UserVO> result = userService.getById(1L);
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getUsername()).isEqualTo("admin");
    }

    @Test
    @DisplayName("getById: 不存在返回 404")
    void shouldReturn404WhenNotFound() {
        when(userMapper.selectById(999L)).thenReturn(null);

        Result<UserVO> result = userService.getById(999L);
        assertThat(result.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("create: 用户名重复返回 400")
    void shouldFailWhenUsernameExists() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        UserDTO dto = new UserDTO();
        dto.setUsername("admin");
        Result<UserVO> result = userService.create(dto);
        assertThat(result.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("create: 成功创建用户")
    void shouldCreateUserSuccessfully() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.insert(any(SysUser.class))).thenReturn(1);
        when(passwordEncoder.encode(any())).thenReturn("encoded");

        UserDTO dto = new UserDTO();
        dto.setUsername("newuser"); dto.setRealName("New User");
        Result<UserVO> result = userService.create(dto);
        assertThat(result.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("update: 更新成功后清除缓存")
    void shouldClearCacheAfterUpdate() {
        stubRedis();
        SysUser user = new SysUser();
        user.setId(1L); user.setUsername("admin");
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.updateById(any(SysUser.class))).thenReturn(1);

        UserDTO dto = new UserDTO();
        dto.setRealName("Updated");
        Result<UserVO> result = userService.update(1L, dto);
        assertThat(result.getCode()).isEqualTo(200);
        verify(redisTemplate).delete("user:1");
        verify(redisTemplate).delete("user:perm:1");
    }

    @Test
    @DisplayName("delete: 删除成功后清除缓存")
    void shouldDeleteUserAndClearCache() {
        stubRedis();
        when(userMapper.deleteById(1L)).thenReturn(1);

        Result<Void> result = userService.delete(1L);
        assertThat(result.getCode()).isEqualTo(200);
        verify(redisTemplate).delete("user:1");
    }

    @Test
    @DisplayName("updateStatus: 禁用/启用用户")
    void shouldUpdateUserStatus() {
        stubRedis();
        SysUser user = new SysUser();
        user.setId(1L);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.updateById(any(SysUser.class))).thenReturn(1);

        Result<Void> result = userService.updateStatus(1L, 0);
        assertThat(result.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("resetPassword: 重置密码成功")
    void shouldResetPassword() {
        SysUser user = new SysUser();
        user.setId(1L);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.updateById(any(SysUser.class))).thenReturn(1);
        when(passwordEncoder.encode("newpass")).thenReturn("encoded");

        Result<Void> result = userService.resetPassword(1L, "newpass");
        assertThat(result.getCode()).isEqualTo(200);
    }
}
