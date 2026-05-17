package com.expenseflow.expense.controller;

import com.expenseflow.common.result.Result;
import com.expenseflow.expense.service.InvoiceService;
import com.expenseflow.expense.vo.InvoiceVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/expense/invoice")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @PreAuthorize("hasAuthority('invoice:upload')")
    @PostMapping("/upload")
    public Result<InvoiceVO> upload(@RequestParam("file") MultipartFile file,
                                     @RequestParam(defaultValue = "ELECTRONIC") String invoiceType) {
        return invoiceService.upload(file, invoiceType);
    }

    @PreAuthorize("hasAuthority('invoice:view')")
    @GetMapping("/page")
    public Result<Page<InvoiceVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String ocrStatus) {
        return invoiceService.page(page, size, ocrStatus);
    }

    @PreAuthorize("hasAuthority('invoice:view')")
    @GetMapping("/{id}")
    public Result<InvoiceVO> getById(@PathVariable Long id) {
        return invoiceService.getById(id);
    }

    @PreAuthorize("hasAuthority('ocr:recognize')")
    @PostMapping("/{id}/ocr")
    public Result<InvoiceVO> triggerOcr(@PathVariable Long id) {
        return invoiceService.triggerOcr(id);
    }
}
