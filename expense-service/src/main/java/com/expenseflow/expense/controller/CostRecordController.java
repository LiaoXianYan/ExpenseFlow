package com.expenseflow.expense.controller;

import com.expenseflow.common.annotation.AuditLog;
import com.expenseflow.common.result.Result;
import com.expenseflow.expense.dto.CostRecordDTO;
import com.expenseflow.expense.entity.ExCostRecord;
import com.expenseflow.expense.service.CostRecordService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/expense/cost")
@RequiredArgsConstructor
public class CostRecordController extends BaseController {

    private final CostRecordService costService;

    @PreAuthorize("hasAuthority('report:view')")
    @GetMapping("/page")
    public Result<Page<ExCostRecord>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return costService.page(page, size, getCurrentUserId());
    }

    @PreAuthorize("hasAuthority('report:edit')")
    @PostMapping
    @AuditLog(module = "消费记录", operation = "CREATE")
    public Result<ExCostRecord> create(@Valid @RequestBody CostRecordDTO dto) {
        return costService.create(dto);
    }

    @PreAuthorize("hasAuthority('report:edit')")
    @PutMapping("/{id}")
    @AuditLog(module = "消费记录", operation = "UPDATE")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CostRecordDTO dto) {
        return costService.update(id, dto);
    }

    @PreAuthorize("hasAuthority('report:edit')")
    @DeleteMapping("/{id}")
    @AuditLog(module = "消费记录", operation = "DELETE")
    public Result<Void> delete(@PathVariable Long id) {
        return costService.delete(id);
    }
}
