package com.expenseflow.expense.service;

import com.expenseflow.common.result.Result;
import com.expenseflow.expense.dto.ExpenseReportDTO;
import com.expenseflow.expense.dto.ExpenseItemDTO;
import com.expenseflow.expense.vo.ExpenseReportVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface ExpenseReportService {
    Result<Page<ExpenseReportVO>> page(int page, int size, Long applicantId, String status);
    Result<ExpenseReportVO> getById(Long id);
    Result<ExpenseReportVO> create(ExpenseReportDTO dto);
    Result<ExpenseReportVO> update(Long id, ExpenseReportDTO dto);
    Result<Void> delete(Long id);
    Result<ExpenseReportVO> submit(Long id);
    Result<ExpenseReportVO> withdraw(Long id);
    Result<ExpenseReportVO> addItem(Long reportId, ExpenseItemDTO itemDTO);
}
