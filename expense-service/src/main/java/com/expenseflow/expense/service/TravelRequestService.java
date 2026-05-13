package com.expenseflow.expense.service;

import com.expenseflow.common.result.Result;
import com.expenseflow.expense.dto.TravelRequestDTO;
import com.expenseflow.expense.vo.TravelRequestVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface TravelRequestService {
    Result<Page<TravelRequestVO>> page(int page, int size, Long applicantId, String status);
    Result<TravelRequestVO> getById(Long id);
    Result<TravelRequestVO> create(TravelRequestDTO dto);
    Result<TravelRequestVO> update(Long id, TravelRequestDTO dto);
    Result<Void> delete(Long id);
    Result<TravelRequestVO> submit(Long id);
    Result<TravelRequestVO> withdraw(Long id);
}
