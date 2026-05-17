package com.expenseflow.system.service;

import java.util.List;

public interface PermissionService {
    /**
     * 获取用户的所有权限码（角色叠加取并集）
     */
    List<String> getPermissionCodesByUserId(Long userId);

    /**
     * 清除用户的权限缓存（角色变更后调用）
     */
    void evictCache(Long userId);
}
