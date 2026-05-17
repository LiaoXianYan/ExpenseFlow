package com.expenseflow.system.controller;

import com.expenseflow.common.result.Result;
import com.expenseflow.system.entity.SysPermission;
import com.expenseflow.system.mapper.SysPermissionMapper;
import com.expenseflow.system.service.PermissionService;
import com.expenseflow.system.vo.PermissionTreeVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/system/permission")
@RequiredArgsConstructor
public class PermissionController {

    private final SysPermissionMapper permissionMapper;
    private final PermissionService permissionService;

    @GetMapping("/tree")
    public Result<List<PermissionTreeVO>> tree() {
        List<SysPermission> all = permissionMapper.selectList(
            new LambdaQueryWrapper<SysPermission>().orderByAsc(SysPermission::getSortOrder));
        Map<Long, List<SysPermission>> parentMap = all.stream()
            .collect(Collectors.groupingBy(p -> p.getParentId() == null ? 0L : p.getParentId()));
        return Result.ok(buildTree(0L, parentMap));
    }

    @GetMapping("/my")
    public Result<List<String>> myPermissions() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = (Long) auth.getPrincipal();
        return Result.ok(permissionService.getPermissionCodesByUserId(userId));
    }

    private List<PermissionTreeVO> buildTree(Long parentId, Map<Long, List<SysPermission>> map) {
        List<SysPermission> children = map.getOrDefault(parentId, Collections.emptyList());
        return children.stream().map(p -> {
            PermissionTreeVO vo = new PermissionTreeVO();
            vo.setId(p.getId());
            vo.setParentId(p.getParentId());
            vo.setPermissionCode(p.getPermissionCode());
            vo.setPermissionName(p.getPermissionName());
            vo.setPermissionType(p.getPermissionType());
            vo.setPath(p.getPath());
            vo.setIcon(p.getIcon());
            vo.setSortOrder(p.getSortOrder());
            vo.setChildren(buildTree(p.getId(), map));
            return vo;
        }).collect(Collectors.toList());
    }
}
