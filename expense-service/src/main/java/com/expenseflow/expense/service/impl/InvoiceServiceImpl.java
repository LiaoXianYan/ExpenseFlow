package com.expenseflow.expense.service.impl;

import com.expenseflow.common.result.Result;
import com.expenseflow.expense.config.OcrConfig;
import com.expenseflow.expense.entity.ExInvoice;
import com.expenseflow.expense.mapper.ExInvoiceMapper;
import com.expenseflow.expense.service.InvoiceService;
import com.expenseflow.expense.vo.InvoiceVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    private final ExInvoiceMapper invoiceMapper;
    private final OcrConfig ocrConfig;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/png", "image/jpeg", "application/pdf");

    @Override
    @Transactional
    public Result<InvoiceVO> upload(MultipartFile file, String invoiceType) {
        if (file.isEmpty()) return Result.fail(400, "文件不能为空");
        if (file.getSize() > 10 * 1024 * 1024) return Result.fail(400, "文件大小不能超过10MB");
        if (!ALLOWED_TYPES.contains(file.getContentType()))
            return Result.fail(400, "仅支持 PNG/JPG/PDF 格式");

        try {
            String ext = getExtension(file.getOriginalFilename());
            String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            Path dir = Paths.get(ocrConfig.getUploadPath(), "0");
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            file.transferTo(target.toFile());

            ExInvoice inv = new ExInvoice();
            inv.setInvoiceType(invoiceType != null ? invoiceType : "ELECTRONIC");
            inv.setImageUrl(target.toString());
            inv.setOcrStatus("PENDING");
            invoiceMapper.insert(inv);

            doOcr(inv.getId(), target.toFile());

            InvoiceVO vo = new InvoiceVO();
            BeanUtils.copyProperties(inv, vo);
            return Result.ok(vo);
        } catch (IOException e) {
            log.error("文件上传失败", e);
            return Result.fail(500, "文件上传失败");
        }
    }

    @Override
    public Result<Page<InvoiceVO>> page(int page, int size, String ocrStatus) {
        LambdaQueryWrapper<ExInvoice> qw = new LambdaQueryWrapper<>();
        if (ocrStatus != null && !ocrStatus.isEmpty()) qw.eq(ExInvoice::getOcrStatus, ocrStatus);
        qw.orderByDesc(ExInvoice::getCreateTime);
        Page<ExInvoice> pg = invoiceMapper.selectPage(new Page<>(page, size), qw);
        Page<InvoiceVO> voPage = new Page<>(page, size, pg.getTotal());
        voPage.setRecords(pg.getRecords().stream().map(i -> {
            InvoiceVO vo = new InvoiceVO();
            BeanUtils.copyProperties(i, vo);
            return vo;
        }).toList());
        return Result.ok(voPage);
    }

    @Override
    public Result<InvoiceVO> getById(Long id) {
        ExInvoice inv = invoiceMapper.selectById(id);
        if (inv == null) return Result.fail(404, "发票不存在");
        InvoiceVO vo = new InvoiceVO();
        BeanUtils.copyProperties(inv, vo);
        return Result.ok(vo);
    }

    @Override
    public Result<InvoiceVO> triggerOcr(Long id) {
        ExInvoice inv = invoiceMapper.selectById(id);
        if (inv == null) return Result.fail(404, "发票不存在");
        inv.setOcrStatus("PROCESSING");
        invoiceMapper.updateById(inv);
        doOcr(id, new java.io.File(inv.getImageUrl()));
        InvoiceVO vo = new InvoiceVO();
        BeanUtils.copyProperties(inv, vo);
        return Result.ok(vo);
    }

    @Async
    void doOcr(Long invoiceId, java.io.File file) {
        ExInvoice inv = invoiceMapper.selectById(invoiceId);
        try {
            Thread.sleep(1500);
            inv.setOcrStatus("SUCCESS");
            inv.setInvoiceNo("MOCK-" + invoiceId);
            inv.setTotalAmount(new BigDecimal("100.00"));
            inv.setAmount(new BigDecimal("94.34"));
            inv.setTaxAmount(new BigDecimal("5.66"));
            inv.setInvoiceDate(LocalDate.now());
            inv.setSellerName("模拟销售方");
            inv.setSellerTaxNo("91310000607335492B");
            inv.setOcrConfidence(new BigDecimal("0.95"));
            inv.setOcrRawResult("{\"mock\": true, \"invoice_id\": " + invoiceId + "}");
            invoiceMapper.updateById(inv);
        } catch (Exception e) {
            log.error("OCR 识别失败", e);
            inv.setOcrStatus("FAILED");
            inv.setOcrRawResult("{\"error\": \"" + e.getMessage() + "\"}");
            invoiceMapper.updateById(inv);
        }
    }

    private String getExtension(String filename) {
        if (filename == null) return "jpg";
        int i = filename.lastIndexOf('.');
        return i == -1 ? "jpg" : filename.substring(i + 1).toLowerCase();
    }
}
