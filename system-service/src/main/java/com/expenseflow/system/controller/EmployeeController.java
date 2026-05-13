package com.expenseflow.system.controller;

import com.expenseflow.common.annotation.AuditLog;
import com.expenseflow.common.result.Result;
import com.expenseflow.system.dto.EmployeeDTO;
import com.expenseflow.system.entity.SysEmployee;
import com.expenseflow.system.mapper.SysEmployeeMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/employee")
@RequiredArgsConstructor
public class EmployeeController {

    private final SysEmployeeMapper employeeMapper;

    @GetMapping("/page")
    public Result<Page<SysEmployee>> page(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size,
                                           @RequestParam(required = false) Long departmentId) {
        LambdaQueryWrapper<SysEmployee> qw = new LambdaQueryWrapper<>();
        if (departmentId != null) qw.eq(SysEmployee::getDepartmentId, departmentId);
        qw.orderByDesc(SysEmployee::getCreateTime);
        return Result.ok(employeeMapper.selectPage(new Page<>(page, size), qw));
    }

    @PostMapping
    @AuditLog(module = "员工管理", operation = "CREATE")
    public Result<SysEmployee> create(@Valid @RequestBody EmployeeDTO dto) {
        SysEmployee emp = new SysEmployee();
        BeanUtils.copyProperties(dto, emp);
        employeeMapper.insert(emp);
        return Result.ok(emp);
    }

    @PutMapping("/{id}")
    @AuditLog(module = "员工管理", operation = "UPDATE")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody EmployeeDTO dto) {
        SysEmployee emp = employeeMapper.selectById(id);
        if (emp == null) return Result.fail(404, "员工不存在");
        BeanUtils.copyProperties(dto, emp);
        emp.setId(id);
        employeeMapper.updateById(emp);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @AuditLog(module = "员工管理", operation = "DELETE")
    public Result<Void> delete(@PathVariable Long id) {
        employeeMapper.deleteById(id);
        return Result.ok();
    }
}
