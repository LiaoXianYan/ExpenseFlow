package com.expenseflow.system.controller;

import com.expenseflow.common.result.Result;
import com.expenseflow.system.dto.TenantDTO;
import com.expenseflow.system.entity.SysTenant;
import com.expenseflow.system.mapper.SysTenantMapper;
import com.expenseflow.system.vo.TenantVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/tenant")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class TenantController {

    private final SysTenantMapper tenantMapper;

    @GetMapping("/page")
    public Result<Page<TenantVO>> page(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "10") int size,
                                        @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<SysTenant> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            qw.and(w -> w.like(SysTenant::getTenantName, keyword)
                    .or().like(SysTenant::getTenantCode, keyword));
        }
        qw.orderByDesc(SysTenant::getCreateTime);
        Page<SysTenant> pg = tenantMapper.selectPage(new Page<>(page, size), qw);
        Page<TenantVO> voPage = new Page<>(page, size, pg.getTotal());
        voPage.setRecords(pg.getRecords().stream().map(this::toVO).toList());
        return Result.ok(voPage);
    }

    @GetMapping("/{id}")
    public Result<TenantVO> getById(@PathVariable Long id) {
        SysTenant t = tenantMapper.selectById(id);
        return t == null ? Result.fail(404, "租户不存在") : Result.ok(toVO(t));
    }

    @PostMapping
    public Result<TenantVO> create(@Valid @RequestBody TenantDTO dto) {
        SysTenant t = new SysTenant();
        BeanUtils.copyProperties(dto, t);
        t.setStatus(1);
        tenantMapper.insert(t);
        return Result.ok(toVO(t));
    }

    @PutMapping("/{id}")
    public Result<TenantVO> update(@PathVariable Long id, @Valid @RequestBody TenantDTO dto) {
        SysTenant t = tenantMapper.selectById(id);
        if (t == null) return Result.fail(404, "租户不存在");
        BeanUtils.copyProperties(dto, t);
        t.setId(id);
        tenantMapper.updateById(t);
        return Result.ok(toVO(t));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        tenantMapper.deleteById(id);
        return Result.ok();
    }

    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        SysTenant t = tenantMapper.selectById(id);
        if (t == null) return Result.fail(404, "租户不存在");
        t.setStatus(status);
        tenantMapper.updateById(t);
        return Result.ok();
    }

    private TenantVO toVO(SysTenant t) {
        TenantVO vo = new TenantVO();
        BeanUtils.copyProperties(t, vo);
        return vo;
    }
}
