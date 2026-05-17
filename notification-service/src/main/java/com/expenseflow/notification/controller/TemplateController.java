package com.expenseflow.notification.controller;

import com.expenseflow.common.result.Result;
import com.expenseflow.notification.dto.TemplateDTO;
import com.expenseflow.notification.service.TemplateService;
import com.expenseflow.notification.vo.TemplateVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notification/template")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @PreAuthorize("hasAuthority('notification:manage')")
    @GetMapping("/list")
    public Result<List<TemplateVO>> list() {
        return Result.ok(templateService.list());
    }

    @PreAuthorize("hasAuthority('notification:manage')")
    @GetMapping("/{code}")
    public Result<TemplateVO> getByCode(@PathVariable String code) {
        TemplateVO vo = templateService.getByCode(code);
        return vo != null ? Result.ok(vo) : Result.fail(404, "模板不存在");
    }

    @PreAuthorize("hasAuthority('notification:manage')")
    @PostMapping
    public Result<TemplateVO> create(@Valid @RequestBody TemplateDTO dto) {
        return Result.ok(templateService.create(dto));
    }

    @PreAuthorize("hasAuthority('notification:manage')")
    @PutMapping("/{id}")
    public Result<TemplateVO> update(@PathVariable Long id, @Valid @RequestBody TemplateDTO dto) {
        return Result.ok(templateService.update(id, dto));
    }
}
