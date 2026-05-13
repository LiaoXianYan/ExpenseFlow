package com.expenseflow.expense.controller;

import com.expenseflow.common.result.Result;
import com.expenseflow.expense.service.InvoiceService;
import com.expenseflow.expense.vo.InvoiceVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/expense/invoice")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @PostMapping("/upload")
    public Result<InvoiceVO> upload(@RequestParam("file") MultipartFile file,
                                     @RequestParam(defaultValue = "ELECTRONIC") String invoiceType) {
        return invoiceService.upload(file, invoiceType);
    }

    @GetMapping("/page")
    public Result<Page<InvoiceVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String ocrStatus) {
        return invoiceService.page(page, size, ocrStatus);
    }

    @GetMapping("/{id}")
    public Result<InvoiceVO> getById(@PathVariable Long id) {
        return invoiceService.getById(id);
    }

    @PostMapping("/{id}/ocr")
    public Result<InvoiceVO> triggerOcr(@PathVariable Long id) {
        return invoiceService.triggerOcr(id);
    }
}
