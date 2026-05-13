package com.expenseflow.expense.controller;

import com.expenseflow.common.annotation.AuditLog;
import com.expenseflow.common.result.Result;
import com.expenseflow.expense.dto.CostRecordDTO;
import com.expenseflow.expense.entity.ExCostRecord;
import com.expenseflow.expense.service.CostRecordService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/expense/cost")
@RequiredArgsConstructor
public class CostRecordController {

    private final CostRecordService costService;

    @GetMapping("/page")
    public Result<Page<ExCostRecord>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader("X-User-Id") Long userId) {
        return costService.page(page, size, userId);
    }

    @PostMapping
    @AuditLog(module = "消费记录", operation = "CREATE")
    public Result<ExCostRecord> create(@Valid @RequestBody CostRecordDTO dto) {
        return costService.create(dto);
    }

    @PutMapping("/{id}")
    @AuditLog(module = "消费记录", operation = "UPDATE")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CostRecordDTO dto) {
        return costService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @AuditLog(module = "消费记录", operation = "DELETE")
    public Result<Void> delete(@PathVariable Long id) {
        return costService.delete(id);
    }
}
