package com.expenseflow.expense.service;

import com.expenseflow.common.result.Result;
import com.expenseflow.expense.vo.InvoiceVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.multipart.MultipartFile;

public interface InvoiceService {
    Result<InvoiceVO> upload(MultipartFile file, String invoiceType);
    Result<Page<InvoiceVO>> page(int page, int size, String ocrStatus);
    Result<InvoiceVO> getById(Long id);
    Result<InvoiceVO> triggerOcr(Long id);
}
