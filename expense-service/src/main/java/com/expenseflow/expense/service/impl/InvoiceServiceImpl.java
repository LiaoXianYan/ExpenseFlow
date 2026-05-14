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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Async
    void doOcr(Long invoiceId, java.io.File file) {
        ExInvoice inv = invoiceMapper.selectById(invoiceId);
        try {
            if (ocrConfig.isMock()) {
                doMockOcr(inv, invoiceId);
            } else {
                doRealOcr(inv, file);
            }
            invoiceMapper.updateById(inv);
        } catch (Exception e) {
            log.error("OCR 识别失败", e);
            inv.setOcrStatus("FAILED");
            inv.setOcrRawResult("{\"error\": \"" + e.getMessage() + "\"}");
            invoiceMapper.updateById(inv);
        }
    }

    private void doMockOcr(ExInvoice inv, Long invoiceId) throws InterruptedException {
        Thread.sleep(800);
        inv.setOcrStatus("SUCCESS");
        inv.setInvoiceNo("MOCK-" + invoiceId);
        inv.setTotalAmount(new BigDecimal("100.00"));
        inv.setAmount(new BigDecimal("94.34"));
        inv.setTaxAmount(new BigDecimal("5.66"));
        inv.setInvoiceDate(LocalDate.now());
        inv.setSellerName("模拟销售方");
        inv.setSellerTaxNo("91310000607335492B");
        inv.setOcrConfidence(new BigDecimal("0.95"));
        inv.setOcrRawResult("{\"mock\":true}");
    }

    private void doRealOcr(ExInvoice inv, java.io.File file) throws Exception {
        byte[] fileBytes = Files.readAllBytes(file.toPath());
        String base64 = Base64.getEncoder().encodeToString(fileBytes);

        String body = objectMapper.writeValueAsString(
            java.util.Map.of("image", base64));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(ocrConfig.getEndpoint()))
            .header("Authorization", "APPCODE " + ocrConfig.getAppCode())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        inv.setOcrRawResult(response.body());

        if (response.statusCode() == 200) {
            @SuppressWarnings("unchecked")
            var map = objectMapper.readValue(response.body(), java.util.Map.class);
            inv.setOcrStatus("SUCCESS");

            if (map.get("invoice_num") != null) inv.setInvoiceNo(map.get("invoice_num").toString());
            if (map.get("total_amount") != null) inv.setTotalAmount(new BigDecimal(map.get("total_amount").toString()));
            if (map.get("invoice_date") != null) inv.setInvoiceDate(LocalDate.parse(map.get("invoice_date").toString()));
            if (map.get("seller_name") != null) inv.setSellerName(map.get("seller_name").toString());
            if (map.get("seller_tax_no") != null) inv.setSellerTaxNo(map.get("seller_tax_no").toString());
            if (map.get("confidence") != null) inv.setOcrConfidence(new BigDecimal(map.get("confidence").toString()));

            log.info("阿里云 OCR 识别成功: invoiceId={}", inv.getInvoiceNo());
        } else {
            inv.setOcrStatus("FAILED");
            log.error("阿里云 OCR 返回非200: status={}", response.statusCode());
        }
    }

    private String getExtension(String filename) {
        if (filename == null) return "jpg";
        int i = filename.lastIndexOf('.');
        return i == -1 ? "jpg" : filename.substring(i + 1).toLowerCase();
    }
}
