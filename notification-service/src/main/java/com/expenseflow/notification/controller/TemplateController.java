package com.expenseflow.notification.controller;

import com.expenseflow.common.result.Result;
import com.expenseflow.notification.dto.TemplateDTO;
import com.expenseflow.notification.service.TemplateService;
import com.expenseflow.notification.vo.TemplateVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notification/template")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @GetMapping("/list")
    public Result<List<TemplateVO>> list() {
        return Result.ok(templateService.list());
    }

    @GetMapping("/{code}")
    public Result<TemplateVO> getByCode(@PathVariable String code) {
        TemplateVO vo = templateService.getByCode(code);
        return vo != null ? Result.ok(vo) : Result.fail(404, "模板不存在");
    }

    @PostMapping
    public Result<TemplateVO> create(@Valid @RequestBody TemplateDTO dto) {
        return Result.ok(templateService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<TemplateVO> update(@PathVariable Long id, @Valid @RequestBody TemplateDTO dto) {
        return Result.ok(templateService.update(id, dto));
    }
}
