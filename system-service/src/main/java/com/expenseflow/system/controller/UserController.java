package com.expenseflow.system.controller;

import com.expenseflow.common.annotation.AuditLog;
import com.expenseflow.common.result.Result;
import com.expenseflow.system.dto.UserDTO;
import com.expenseflow.system.service.UserService;
import com.expenseflow.system.vo.UserVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PreAuthorize("hasAuthority('user:view')")
    @GetMapping("/page")
    public Result<Page<UserVO>> page(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "10") int size,
                                      @RequestParam(required = false) String keyword) {
        return userService.page(page, size, keyword);
    }

    @PreAuthorize("hasAuthority('user:view')")
    @GetMapping("/{id}")
    public Result<UserVO> getById(@PathVariable Long id) {
        return userService.getById(id);
    }

    @PreAuthorize("hasAuthority('user:create')")
    @PostMapping
    @AuditLog(module = "用户管理", operation = "CREATE")
    public Result<UserVO> create(@Valid @RequestBody UserDTO dto) {
        return userService.create(dto);
    }

    @PreAuthorize("hasAuthority('user:edit')")
    @PutMapping("/{id}")
    @AuditLog(module = "用户管理", operation = "UPDATE")
    public Result<UserVO> update(@PathVariable Long id, @Valid @RequestBody UserDTO dto) {
        return userService.update(id, dto);
    }

    @PreAuthorize("hasAuthority('user:delete')")
    @DeleteMapping("/{id}")
    @AuditLog(module = "用户管理", operation = "DELETE")
    public Result<Void> delete(@PathVariable Long id) {
        return userService.delete(id);
    }

    @PatchMapping("/{id}/status")
    @AuditLog(module = "用户管理", operation = "UPDATE_STATUS")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        return userService.updateStatus(id, status);
    }

    @PatchMapping("/{id}/password")
    @AuditLog(module = "用户管理", operation = "RESET_PASSWORD")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestParam String password) {
        return userService.resetPassword(id, password);
    }
}
