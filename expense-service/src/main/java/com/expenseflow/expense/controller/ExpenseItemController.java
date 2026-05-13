package com.expenseflow.expense.controller;

import com.expenseflow.common.annotation.AuditLog;
import com.expenseflow.common.result.Result;
import com.expenseflow.expense.dto.ExpenseItemDTO;
import com.expenseflow.expense.entity.ExExpenseItem;
import com.expenseflow.expense.mapper.ExExpenseItemMapper;
import com.expenseflow.expense.service.ExpenseReportService;
import com.expenseflow.expense.vo.ExpenseReportVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/expense/report/{reportId}/item")
@RequiredArgsConstructor
public class ExpenseItemController {

    private final ExExpenseItemMapper itemMapper;
    private final ExpenseReportService reportService;

    @GetMapping("/list")
    public Result<List<ExExpenseItem>> list(@PathVariable Long reportId) {
        return Result.ok(itemMapper.selectList(
            new LambdaQueryWrapper<ExExpenseItem>().eq(ExExpenseItem::getReportId, reportId)));
    }

    @PostMapping
    @AuditLog(module = "报销明细", operation = "ADD")
    public Result<ExpenseReportVO> add(@PathVariable Long reportId, @Valid @RequestBody ExpenseItemDTO dto) {
        return reportService.addItem(reportId, dto);
    }

    @DeleteMapping("/{itemId}")
    @AuditLog(module = "报销明细", operation = "DELETE")
    public Result<Void> delete(@PathVariable Long reportId, @PathVariable Long itemId) {
        itemMapper.deleteById(itemId);
        return Result.ok();
    }
}
