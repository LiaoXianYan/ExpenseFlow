package com.expenseflow.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.expenseflow.system.entity.SysPermission;
import com.expenseflow.system.entity.SysRolePermission;
import com.expenseflow.system.entity.SysUserRole;
import com.expenseflow.system.mapper.SysPermissionMapper;
import com.expenseflow.system.mapper.SysRolePermissionMapper;
import com.expenseflow.system.mapper.SysUserRoleMapper;
import com.expenseflow.system.service.PermissionService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final SysUserRoleMapper userRoleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysPermissionMapper permissionMapper;

    private final Cache<Long, List<String>> userPermissionCache = Caffeine.newBuilder()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .maximumSize(1000)
        .build();

    @Override
    public List<String> getPermissionCodesByUserId(Long userId) {
        return userPermissionCache.get(userId, this::loadPermissions);
    }

    @Override
    public void evictCache(Long userId) {
        userPermissionCache.invalidate(userId);
    }

    private List<String> loadPermissions(Long userId) {
        List<Long> roleIds = userRoleMapper.selectList(
            new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId)
        ).stream().map(SysUserRole::getRoleId).toList();

        if (roleIds.isEmpty()) return Collections.emptyList();

        List<Long> permissionIds = rolePermissionMapper.selectList(
            new LambdaQueryWrapper<SysRolePermission>()
                .in(SysRolePermission::getRoleId, roleIds)
        ).stream().map(SysRolePermission::getPermissionId).distinct().toList();

        if (permissionIds.isEmpty()) return Collections.emptyList();

        return permissionMapper.selectList(
            new LambdaQueryWrapper<SysPermission>()
                .in(SysPermission::getId, permissionIds)
        ).stream().map(SysPermission::getPermissionCode).distinct().toList();
    }
}
