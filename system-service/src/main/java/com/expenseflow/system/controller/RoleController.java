package com.expenseflow.system.controller;

import com.expenseflow.common.annotation.AuditLog;
import com.expenseflow.common.result.Result;
import com.expenseflow.system.dto.RoleAssignPermsDTO;
import com.expenseflow.system.dto.RoleAssignUsersDTO;
import com.expenseflow.system.dto.RoleDTO;
import com.expenseflow.system.entity.SysRole;
import com.expenseflow.system.entity.SysUserRole;
import com.expenseflow.system.entity.SysRolePermission;
import com.expenseflow.system.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/system/role")
@RequiredArgsConstructor
public class RoleController {

    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRolePermissionMapper rolePermMapper;

    @GetMapping("/list")
    public Result<List<SysRole>> list() {
        return Result.ok(roleMapper.selectList(
            new LambdaQueryWrapper<SysRole>().orderByDesc(SysRole::getCreateTime)));
    }

    @PostMapping
    @AuditLog(module = "角色管理", operation = "CREATE")
    public Result<SysRole> create(@Valid @RequestBody RoleDTO dto) {
        SysRole role = new SysRole();
        role.setRoleCode(dto.getRoleCode());
        role.setRoleName(dto.getRoleName());
        role.setRoleType(2);
        role.setStatus(1);
        roleMapper.insert(role);
        return Result.ok(role);
    }

    @PutMapping("/{id}")
    @AuditLog(module = "角色管理", operation = "UPDATE")
    public Result<SysRole> update(@PathVariable Long id, @Valid @RequestBody RoleDTO dto) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) return Result.fail(404, "角色不存在");
        role.setRoleCode(dto.getRoleCode());
        role.setRoleName(dto.getRoleName());
        roleMapper.updateById(role);
        return Result.ok(role);
    }

    @DeleteMapping("/{id}")
    @AuditLog(module = "角色管理", operation = "DELETE")
    @Transactional
    public Result<Void> delete(@PathVariable Long id) {
        roleMapper.deleteById(id);
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
        rolePermMapper.delete(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, id));
        return Result.ok();
    }

    @PostMapping("/{id}/users")
    @AuditLog(module = "角色管理", operation = "ASSIGN_USERS")
    @Transactional
    public Result<Void> assignUsers(@PathVariable Long id, @Valid @RequestBody RoleAssignUsersDTO dto) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
        if (dto.getUserIds() != null && !dto.getUserIds().isEmpty()) {
            for (Long userId : dto.getUserIds()) {
                SysUserRole ur = new SysUserRole();
                ur.setRoleId(id);
                ur.setUserId(userId);
                userRoleMapper.insert(ur);
            }
        }
        return Result.ok();
    }

    @PostMapping("/{id}/permissions")
    @AuditLog(module = "角色管理", operation = "ASSIGN_PERMISSIONS")
    @Transactional
    public Result<Void> assignPermissions(@PathVariable Long id, @Valid @RequestBody RoleAssignPermsDTO dto) {
        rolePermMapper.delete(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, id));
        if (dto.getPermissionIds() != null && !dto.getPermissionIds().isEmpty()) {
            for (Long permId : dto.getPermissionIds()) {
                SysRolePermission rp = new SysRolePermission();
                rp.setRoleId(id);
                rp.setPermissionId(permId);
                rolePermMapper.insert(rp);
            }
        }
        return Result.ok();
    }
}
