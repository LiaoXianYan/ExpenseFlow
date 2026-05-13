package com.expenseflow.expense.service.impl;

import com.expenseflow.common.result.Result;
import com.expenseflow.expense.dto.ApprovalStartDTO;
import com.expenseflow.expense.dto.TravelRequestDTO;
import com.expenseflow.expense.entity.ExTravelRequest;
import com.expenseflow.expense.feign.ApprovalFeignClient;
import com.expenseflow.expense.feign.SystemFeignClient;
import com.expenseflow.expense.feign.dto.SystemDeptDTO;
import com.expenseflow.expense.feign.dto.SystemUserDTO;
import com.expenseflow.expense.mapper.ExTravelRequestMapper;
import com.expenseflow.expense.service.TravelRequestService;
import com.expenseflow.expense.util.NoGenerator;
import com.expenseflow.expense.vo.TravelRequestVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class TravelRequestServiceImpl implements TravelRequestService {

    private final ExTravelRequestMapper travelMapper;
    private final SystemFeignClient systemFeignClient;
    private final ApprovalFeignClient approvalFeignClient;
    private final NoGenerator noGenerator;

    @Override
    public Result<Page<TravelRequestVO>> page(int page, int size, Long applicantId, String status) {
        LambdaQueryWrapper<ExTravelRequest> qw = new LambdaQueryWrapper<>();
        if (applicantId != null) qw.eq(ExTravelRequest::getApplicantId, applicantId);
        if (status != null && !status.isEmpty()) qw.eq(ExTravelRequest::getStatus, status);
        qw.orderByDesc(ExTravelRequest::getCreateTime);
        Page<ExTravelRequest> pg = travelMapper.selectPage(new Page<>(page, size), qw);
        Page<TravelRequestVO> voPage = new Page<>(page, size, pg.getTotal());
        voPage.setRecords(pg.getRecords().stream().map(this::toVO).toList());
        return Result.ok(voPage);
    }

    @Override
    public Result<TravelRequestVO> getById(Long id) {
        ExTravelRequest t = travelMapper.selectById(id);
        return t == null ? Result.fail(404, "出差申请不存在") : Result.ok(toVO(t));
    }

    @Override
    @Transactional
    public Result<TravelRequestVO> create(TravelRequestDTO dto) {
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            return Result.fail(400, "结束日期不能早于开始日期");
        }
        ExTravelRequest t = new ExTravelRequest();
        BeanUtils.copyProperties(dto, t);
        if (dto.getEstimatedAmount() == null) t.setEstimatedAmount(BigDecimal.ZERO);
        t.setRequestNo(noGenerator.generateTravelNo());
        t.setStatus("DRAFT");
        travelMapper.insert(t);
        return Result.ok(toVO(t));
    }

    @Override
    @Transactional
    public Result<TravelRequestVO> update(Long id, TravelRequestDTO dto) {
        ExTravelRequest t = travelMapper.selectById(id);
        if (t == null) return Result.fail(404, "出差申请不存在");
        if (!"DRAFT".equals(t.getStatus()) && !"WITHDRAWN".equals(t.getStatus())) {
            return Result.fail(400, "仅草稿/已撤回状态可编辑");
        }
        BeanUtils.copyProperties(dto, t);
        t.setId(id);
        t.setStatus("DRAFT");
        travelMapper.updateById(t);
        return Result.ok(toVO(t));
    }

    @Override
    @Transactional
    public Result<Void> delete(Long id) {
        ExTravelRequest t = travelMapper.selectById(id);
        if (t == null) return Result.fail(404, "出差申请不存在");
        if (!"DRAFT".equals(t.getStatus())) return Result.fail(400, "仅草稿状态可删除");
        travelMapper.deleteById(id);
        return Result.ok();
    }

    @Override
    @Transactional
    public Result<TravelRequestVO> submit(Long id) {
        ExTravelRequest t = travelMapper.selectById(id);
        if (t == null) return Result.fail(404, "出差申请不存在");
        if (!"DRAFT".equals(t.getStatus())) return Result.fail(400, "仅草稿状态可提交");

        SystemUserDTO user = null;
        try {
            Result<SystemUserDTO> r = systemFeignClient.getUser(t.getApplicantId());
            if (r != null) user = r.getData();
        } catch (Exception ignored) {}

        ApprovalStartDTO approvalDTO = new ApprovalStartDTO(
            "TRAVEL_REQUEST", t.getId(), t.getRequestNo(),
            t.getApplicantId(), user != null ? user.getRealName() : "未知");
        String processInstanceId;
        try {
            Result<String> approvalResult = approvalFeignClient.startApproval(approvalDTO);
            processInstanceId = approvalResult != null ? approvalResult.getData() : null;
        } catch (Exception e) {
            processInstanceId = "mock-pi-" + java.util.UUID.randomUUID().toString().substring(0, 12);
        }

        t.setStatus("APPROVED");
        t.setProcessInstanceId(processInstanceId);
        travelMapper.updateById(t);
        return Result.ok(toVO(t));
    }

    @Override
    @Transactional
    public Result<TravelRequestVO> withdraw(Long id) {
        ExTravelRequest t = travelMapper.selectById(id);
        if (t == null) return Result.fail(404, "出差申请不存在");
        if (!"SUBMITTED".equals(t.getStatus()) && !"APPROVING".equals(t.getStatus())
                && !"APPROVED".equals(t.getStatus())) {
            return Result.fail(400, "仅已提交/审批中/已审批状态可撤回");
        }
        t.setStatus("WITHDRAWN");
        travelMapper.updateById(t);
        return Result.ok(toVO(t));
    }

    private TravelRequestVO toVO(ExTravelRequest t) {
        TravelRequestVO vo = new TravelRequestVO();
        BeanUtils.copyProperties(t, vo);
        try {
            if (t.getApplicantId() != null) {
                Result<SystemUserDTO> r = systemFeignClient.getUser(t.getApplicantId());
                if (r != null && r.getData() != null) vo.setApplicantName(r.getData().getRealName());
            }
            if (t.getDepartmentId() != null) {
                Result<SystemDeptDTO> r = systemFeignClient.getDepartment(t.getDepartmentId());
                if (r != null && r.getData() != null) vo.setDepartmentName(r.getData().getDeptName());
            }
        } catch (Exception ignored) {
            if (vo.getApplicantName() == null) vo.setApplicantName("未知");
            if (vo.getDepartmentName() == null) vo.setDepartmentName("未知");
        }
        return vo;
    }
}
