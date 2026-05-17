package com.expenseflow.system.service.impl;

import com.expenseflow.common.result.Result;
import com.expenseflow.common.util.JwtUtil;
import com.expenseflow.system.dto.LoginDTO;
import com.expenseflow.system.entity.SysUser;
import com.expenseflow.system.entity.SysUserRole;
import com.expenseflow.system.entity.SysRole;
import com.expenseflow.system.mapper.SysUserMapper;
import com.expenseflow.system.mapper.SysUserRoleMapper;
import com.expenseflow.system.mapper.SysRoleMapper;
import com.expenseflow.system.service.AuthService;
import com.expenseflow.system.service.PermissionService;
import com.expenseflow.system.vo.TokenVO;
import com.expenseflow.system.vo.UserVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;
    private final PermissionService permissionService;

    @Override
    public Result<TokenVO> login(LoginDTO dto) {
        SysUser user = userMapper.selectOne(
            new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, dto.getUsername()));
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            return Result.fail(401, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            return Result.fail(403, "账号已被禁用");
        }

        // 查询用户角色
        List<String> roleCodes = Collections.emptyList();
        List<SysUserRole> userRoles = userRoleMapper.selectList(
            new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, user.getId()));
        if (!userRoles.isEmpty()) {
            List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).toList();
            List<SysRole> roles = roleMapper.selectBatchIds(roleIds);
            roleCodes = roles.stream().map(SysRole::getRoleCode).collect(Collectors.toList());
            redisTemplate.opsForValue().set("user:perm:" + user.getId(), new HashSet<>(roleCodes), 30, TimeUnit.MINUTES);
        }

        List<String> permissions = permissionService.getPermissionCodesByUserId(user.getId());

        String tokenId = UUID.randomUUID().toString().replace("-", "");
        String accessToken = JwtUtil.generateAccessToken(user.getId(), user.getTenantId(), tokenId, roleCodes, permissions, user.getUsername());
        String refreshToken = JwtUtil.generateRefreshToken(user.getId(), user.getTenantId(), tokenId, roleCodes, permissions, user.getUsername());

        UserVO userVO = toUserVO(user);
        // Cache user info
        redisTemplate.opsForValue().set("user:" + user.getId(), userVO, 30, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set("token:" + tokenId, "1", 2, TimeUnit.HOURS);

        return Result.ok(new TokenVO(accessToken, refreshToken, 7200, userVO));
    }

    @Override
    public Result<Void> logout(String token) {
        Claims claims = JwtUtil.parseToken(token);
        if (claims != null) {
            String tokenId = JwtUtil.getTokenId(claims);
            long expireMs = claims.getExpiration().getTime() - System.currentTimeMillis();
            redisTemplate.opsForValue().set("token:blacklist:" + tokenId, "1",
                Math.max(expireMs, 1000), TimeUnit.MILLISECONDS);
        }
        return Result.ok();
    }

    @Override
    public Result<TokenVO> refresh(String refreshToken) {
        Claims claims = JwtUtil.parseToken(refreshToken);
        if (claims == null || JwtUtil.isExpired(claims)) {
            return Result.fail(401, "RefreshToken 无效或已过期");
        }
        Long userId = JwtUtil.getUserId(claims);
        Long tenantId = JwtUtil.getTenantId(claims);
        List<String> roleCodes = JwtUtil.getRoles(claims);

        // re-query roles from DB for freshness
        List<SysUserRole> userRoles = userRoleMapper.selectList(
            new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (!userRoles.isEmpty()) {
            List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).toList();
            List<SysRole> roles = roleMapper.selectBatchIds(roleIds);
            roleCodes = roles.stream().map(SysRole::getRoleCode).collect(Collectors.toList());
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return Result.fail(404, "用户不存在");
        }

        if (roleCodes.isEmpty()) {
            roleCodes = JwtUtil.getRoles(claims); // fallback to JWT claims
        }

        List<String> permissions = permissionService.getPermissionCodesByUserId(userId);

        String newTokenId = UUID.randomUUID().toString().replace("-", "");
        String newAccessToken = JwtUtil.generateAccessToken(userId, tenantId, newTokenId, roleCodes, permissions, user.getUsername());
        String newRefreshToken = JwtUtil.generateRefreshToken(userId, tenantId, newTokenId, roleCodes, permissions, user.getUsername());

        redisTemplate.opsForValue().set("token:" + newTokenId, "1", 2, TimeUnit.HOURS);
        return Result.ok(new TokenVO(newAccessToken, newRefreshToken, 7200, toUserVO(user)));
    }

    @Override
    public Result<UserVO> me(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return Result.fail(404, "用户不存在");
        }
        return Result.ok(toUserVO(user));
    }

    private UserVO toUserVO(SysUser user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}
