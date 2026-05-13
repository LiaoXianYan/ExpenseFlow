package com.expenseflow.expense.service.impl;

import com.expenseflow.common.result.Result;
import com.expenseflow.expense.dto.ApprovalStartDTO;
import com.expenseflow.expense.dto.ExpenseItemDTO;
import com.expenseflow.expense.dto.ExpenseReportDTO;
import com.expenseflow.expense.entity.*;
import com.expenseflow.expense.feign.ApprovalFeignClient;
import com.expenseflow.expense.feign.SystemFeignClient;
import com.expenseflow.expense.feign.dto.SystemDeptDTO;
import com.expenseflow.expense.feign.dto.SystemUserDTO;
import com.expenseflow.expense.mapper.*;
import com.expenseflow.expense.service.ExpenseReportService;
import com.expenseflow.expense.util.NoGenerator;
import com.expenseflow.expense.util.PolicyValidator;
import com.expenseflow.expense.vo.ExpenseItemVO;
import com.expenseflow.expense.vo.ExpenseReportVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseReportServiceImpl implements ExpenseReportService {

    private final ExExpenseReportMapper reportMapper;
    private final ExExpenseItemMapper itemMapper;
    private final ExTravelRequestMapper travelMapper;
    private final SystemFeignClient systemFeignClient;
    private final ApprovalFeignClient approvalFeignClient;
    private final NoGenerator noGenerator;
    private final PolicyValidator policyValidator;

    @Override
    public Result<Page<ExpenseReportVO>> page(int page, int size, Long applicantId, String status) {
        LambdaQueryWrapper<ExExpenseReport> qw = new LambdaQueryWrapper<>();
        if (applicantId != null) qw.eq(ExExpenseReport::getApplicantId, applicantId);
        if (status != null && !status.isEmpty()) qw.eq(ExExpenseReport::getStatus, status);
        qw.orderByDesc(ExExpenseReport::getCreateTime);
        Page<ExExpenseReport> pg = reportMapper.selectPage(new Page<>(page, size), qw);
        Page<ExpenseReportVO> voPage = new Page<>(page, size, pg.getTotal());
        voPage.setRecords(pg.getRecords().stream().map(this::toVO).toList());
        return Result.ok(voPage);
    }

    @Override
    public Result<ExpenseReportVO> getById(Long id) {
        ExExpenseReport r = reportMapper.selectById(id);
        return r == null ? Result.fail(404, "报销单不存在") : Result.ok(toVO(r));
    }

    @Override
    @Transactional
    public Result<ExpenseReportVO> create(ExpenseReportDTO dto) {
        ExExpenseReport r = new ExExpenseReport();
        BeanUtils.copyProperties(dto, r);
        r.setReportNo(noGenerator.generateReportNo());
        r.setTotalAmount(BigDecimal.ZERO);
        r.setStatus("DRAFT");
        if (dto.getTravelRequestId() != null) {
            ExTravelRequest travel = travelMapper.selectById(dto.getTravelRequestId());
            if (travel == null) return Result.fail(400, "关联的出差申请不存在");
        }
        reportMapper.insert(r);
        return Result.ok(toVO(r));
    }

    @Override
    @Transactional
    public Result<ExpenseReportVO> update(Long id, ExpenseReportDTO dto) {
        ExExpenseReport r = reportMapper.selectById(id);
        if (r == null) return Result.fail(404, "报销单不存在");
        if (!"DRAFT".equals(r.getStatus())) return Result.fail(400, "仅草稿状态可编辑");
        BeanUtils.copyProperties(dto, r);
        r.setId(id);
        reportMapper.updateById(r);
        return Result.ok(toVO(r));
    }

    @Override
    @Transactional
    public Result<Void> delete(Long id) {
        ExExpenseReport r = reportMapper.selectById(id);
        if (r == null) return Result.fail(404, "报销单不存在");
        if (!"DRAFT".equals(r.getStatus())) return Result.fail(400, "仅草稿状态可删除");
        itemMapper.delete(new LambdaQueryWrapper<ExExpenseItem>().eq(ExExpenseItem::getReportId, id));
        reportMapper.deleteById(id);
        return Result.ok();
    }

    @Override
    @Transactional
    public Result<ExpenseReportVO> submit(Long id) {
        ExExpenseReport r = reportMapper.selectById(id);
        if (r == null) return Result.fail(404, "报销单不存在");
        if (!"DRAFT".equals(r.getStatus())) return Result.fail(400, "仅草稿状态可提交");

        long itemCount = itemMapper.selectCount(
            new LambdaQueryWrapper<ExExpenseItem>().eq(ExExpenseItem::getReportId, id));
        if (itemCount == 0) return Result.fail(400, "请至少添加一条报销明细");

        List<ExExpenseItem> items = itemMapper.selectList(
            new LambdaQueryWrapper<ExExpenseItem>().eq(ExExpenseItem::getReportId, id));
        BigDecimal total = items.stream().map(ExExpenseItem::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        r.setTotalAmount(total);

        // Policy validation - non-blocking
        policyValidator.validate(items);

        SystemUserDTO user = null;
        try {
            Result<SystemUserDTO> ru = systemFeignClient.getUser(r.getApplicantId());
            if (ru != null) user = ru.getData();
        } catch (Exception ignored) {}

        ApprovalStartDTO approvalDTO = new ApprovalStartDTO(
            "EXPENSE_REPORT", r.getId(), r.getReportNo(),
            r.getApplicantId(), user != null ? user.getRealName() : "未知");
        Result<String> approvalResult = approvalFeignClient.startApproval(approvalDTO);

        r.setStatus("APPROVED");
        r.setProcessInstanceId(approvalResult.getData());
        reportMapper.updateById(r);
        return Result.ok(toVO(r));
    }

    @Override
    @Transactional
    public Result<ExpenseReportVO> withdraw(Long id) {
        ExExpenseReport r = reportMapper.selectById(id);
        if (r == null) return Result.fail(404, "报销单不存在");
        if (!"SUBMITTED".equals(r.getStatus()) && !"APPROVING".equals(r.getStatus())
                && !"APPROVED".equals(r.getStatus())) {
            return Result.fail(400, "仅已提交/审批中/已审批状态可撤回");
        }
        r.setStatus("WITHDRAWN");
        reportMapper.updateById(r);
        return Result.ok(toVO(r));
    }

    @Override
    @Transactional
    public Result<ExpenseReportVO> addItem(Long reportId, ExpenseItemDTO itemDTO) {
        ExExpenseReport r = reportMapper.selectById(reportId);
        if (r == null) return Result.fail(404, "报销单不存在");
        if (!"DRAFT".equals(r.getStatus())) return Result.fail(400, "仅草稿状态可添加明细");

        ExExpenseItem item = new ExExpenseItem();
        BeanUtils.copyProperties(itemDTO, item);
        item.setReportId(reportId);
        itemMapper.insert(item);

        List<ExExpenseItem> items = itemMapper.selectList(
            new LambdaQueryWrapper<ExExpenseItem>().eq(ExExpenseItem::getReportId, reportId));
        BigDecimal total = items.stream().map(ExExpenseItem::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        r.setTotalAmount(total);
        reportMapper.updateById(r);

        return Result.ok(toVO(r));
    }

    private ExpenseReportVO toVO(ExExpenseReport r) {
        ExpenseReportVO vo = new ExpenseReportVO();
        BeanUtils.copyProperties(r, vo);
        try {
            if (r.getApplicantId() != null) {
                Result<SystemUserDTO> ru = systemFeignClient.getUser(r.getApplicantId());
                if (ru != null && ru.getData() != null) vo.setApplicantName(ru.getData().getRealName());
            }
            if (r.getDepartmentId() != null) {
                Result<SystemDeptDTO> rd = systemFeignClient.getDepartment(r.getDepartmentId());
                if (rd != null && rd.getData() != null) vo.setDepartmentName(rd.getData().getDeptName());
            }
            if (r.getTravelRequestId() != null) {
                ExTravelRequest travel = travelMapper.selectById(r.getTravelRequestId());
                if (travel != null) vo.setTravelRequestNo(travel.getRequestNo());
            }
        } catch (Exception ignored) {}
        List<ExExpenseItem> items = itemMapper.selectList(
            new LambdaQueryWrapper<ExExpenseItem>().eq(ExExpenseItem::getReportId, r.getId()));
        vo.setItems(items.stream().map(i -> {
            ExpenseItemVO iv = new ExpenseItemVO();
            BeanUtils.copyProperties(i, iv);
            return iv;
        }).toList());
        return vo;
    }
}
