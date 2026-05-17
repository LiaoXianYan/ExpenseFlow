package com.expenseflow.system.controller;

import com.expenseflow.common.annotation.AuditLog;
import com.expenseflow.common.result.Result;
import com.expenseflow.system.dto.DepartmentDTO;
import com.expenseflow.system.entity.SysDepartment;
import com.expenseflow.system.mapper.SysDepartmentMapper;
import com.expenseflow.system.vo.DeptTreeVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/system/department")
@RequiredArgsConstructor
public class DepartmentController {

    private final SysDepartmentMapper deptMapper;

    @PreAuthorize("hasAuthority('user:view')")
    @GetMapping("/tree")
    public Result<List<DeptTreeVO>> tree() {
        List<SysDepartment> all = deptMapper.selectList(
            new LambdaQueryWrapper<SysDepartment>().orderByAsc(SysDepartment::getSortOrder));
        Map<Long, List<SysDepartment>> parentMap = all.stream()
            .collect(Collectors.groupingBy(d -> d.getParentId() == null ? 0L : d.getParentId()));
        return Result.ok(buildTree(0L, parentMap));
    }

    @PreAuthorize("hasAuthority('user:edit')")
    @PostMapping
    @AuditLog(module = "部门管理", operation = "CREATE")
    public Result<DeptTreeVO> create(@Valid @RequestBody DepartmentDTO dto) {
        SysDepartment dept = new SysDepartment();
        BeanUtils.copyProperties(dto, dept);
        deptMapper.insert(dept);
        DeptTreeVO vo = new DeptTreeVO();
        BeanUtils.copyProperties(dept, vo);
        return Result.ok(vo);
    }

    @PreAuthorize("hasAuthority('user:edit')")
    @PutMapping("/{id}")
    @AuditLog(module = "部门管理", operation = "UPDATE")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DepartmentDTO dto) {
        SysDepartment dept = deptMapper.selectById(id);
        if (dept == null) return Result.fail(404, "部门不存在");
        BeanUtils.copyProperties(dto, dept);
        dept.setId(id);
        deptMapper.updateById(dept);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('user:edit')")
    @DeleteMapping("/{id}")
    @AuditLog(module = "部门管理", operation = "DELETE")
    public Result<Void> delete(@PathVariable Long id) {
        deptMapper.deleteById(id);
        return Result.ok();
    }

    private List<DeptTreeVO> buildTree(Long parentId, Map<Long, List<SysDepartment>> map) {
        List<SysDepartment> children = map.getOrDefault(parentId, Collections.emptyList());
        return children.stream().map(d -> {
            DeptTreeVO vo = new DeptTreeVO();
            BeanUtils.copyProperties(d, vo);
            vo.setChildren(buildTree(d.getId(), map));
            return vo;
        }).collect(Collectors.toList());
    }
}
