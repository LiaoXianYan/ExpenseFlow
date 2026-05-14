package com.expenseflow.approval.service.impl;

import com.expenseflow.approval.dto.TaskCompleteDTO;
import com.expenseflow.approval.entity.ApApprovalRecord;
import com.expenseflow.approval.feign.SystemFeignClient;
import com.expenseflow.approval.mapper.ApApprovalRecordMapper;
import com.expenseflow.approval.service.ApprovalTaskService;
import com.expenseflow.approval.vo.ApprovalRecordVO;
import com.expenseflow.approval.vo.ApprovalTaskVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.springframework.beans.BeanUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalTaskServiceImpl implements ApprovalTaskService {

    private final TaskService taskService;
    private final ApApprovalRecordMapper recordMapper;
    private final SystemFeignClient systemFeignClient;

    @Override
    public List<ApprovalTaskVO> listTasks(String candidateGroup) {
        TaskQuery query = taskService.createTaskQuery().orderByTaskCreateTime().desc();
        if (candidateGroup != null && !candidateGroup.isEmpty()) {
            query = query.taskCandidateGroup(candidateGroup);
        }
        List<Task> tasks = query.list();
        return tasks.stream().map(task -> {
            ApprovalTaskVO vo = new ApprovalTaskVO();
            vo.setTaskId(task.getId());
            vo.setTaskName(task.getName());
            vo.setTaskDefinitionKey(task.getTaskDefinitionKey());
            vo.setProcessInstanceId(task.getProcessInstanceId());
            vo.setAssignee(task.getAssignee());
            vo.setCreateTime(task.getCreateTime().toInstant()
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());

            // 填充流程变量
            Map<String, Object> vars = taskService.getVariables(task.getId());
            vo.setBusinessType((String) vars.get("businessType"));
            vo.setBusinessId((Long) vars.get("businessId"));
            vo.setRequestNo((String) vars.get("requestNo"));
            vo.setApplicantId((Long) vars.get("applicantId"));
            vo.setApplicantName((String) vars.get("applicantName"));
            return vo;
        }).toList();
    }

    @Override
    public List<ApprovalTaskVO> listTasksByAssignee(Long userId) {
        List<Task> tasks = taskService.createTaskQuery()
            .taskAssignee(String.valueOf(userId))
            .orderByTaskCreateTime().desc()
            .list();
        return tasks.stream().map(task -> {
            ApprovalTaskVO vo = new ApprovalTaskVO();
            vo.setTaskId(task.getId());
            vo.setTaskName(task.getName());
            vo.setTaskDefinitionKey(task.getTaskDefinitionKey());
            vo.setProcessInstanceId(task.getProcessInstanceId());
            vo.setAssignee(task.getAssignee());
            vo.setCreateTime(task.getCreateTime().toInstant()
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
            Map<String, Object> vars = taskService.getVariables(task.getId());
            vo.setBusinessType((String) vars.get("businessType"));
            vo.setBusinessId((Long) vars.get("businessId"));
            vo.setRequestNo((String) vars.get("requestNo"));
            vo.setApplicantId((Long) vars.get("applicantId"));
            vo.setApplicantName((String) vars.get("applicantName"));
            return vo;
        }).toList();
    }

    @Override
    @Transactional
    public void completeTask(String taskId, Long approverId, String approverName, TaskCompleteDTO dto) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }

        // 认领任务（如果尚未认领）
        if (task.getAssignee() == null) {
            taskService.claim(taskId, String.valueOf(approverId));
        }

        // 写审批记录
        ApApprovalRecord record = new ApApprovalRecord();
        record.setBusinessType((String) taskService.getVariable(taskId, "businessType"));
        record.setBusinessId((Long) taskService.getVariable(taskId, "businessId"));
        record.setProcessInstanceId(task.getProcessInstanceId());
        record.setTaskId(taskId);
        record.setTaskName(task.getName());
        record.setApproverId(approverId);
        record.setApproverName(approverName != null ? approverName : "未知");
        record.setTenantId(getCurrentTenantId());
        record.setAction(dto.getAction());
        record.setComment(dto.getComment());
        record.setActionTime(LocalDateTime.now());
        recordMapper.insert(record);
        log.info("审批记录写入: taskId={}, action={}, approver={}", taskId, dto.getAction(), approverId);

        // 设置流程变量 outcome
        Map<String, Object> variables = new HashMap<>();
        variables.put("outcome", dto.getAction());

        taskService.complete(taskId, variables);
        log.info("任务完成: taskId={}, action={}", taskId, dto.getAction());
    }

    @Override
    public void delegateTask(String taskId, Long fromUserId, String delegateToUser) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }

        // 写委派记录
        ApApprovalRecord record = new ApApprovalRecord();
        record.setBusinessType((String) taskService.getVariable(taskId, "businessType"));
        record.setBusinessId((Long) taskService.getVariable(taskId, "businessId"));
        record.setProcessInstanceId(task.getProcessInstanceId());
        record.setTaskId(taskId);
        record.setTaskName(task.getName());
        record.setApproverId(fromUserId);
        record.setTenantId(getCurrentTenantId());
        record.setAction("DELEGATE");
        record.setComment("委派给 " + delegateToUser);
        record.setActionTime(LocalDateTime.now());
        recordMapper.insert(record);

        // Flowable 委派
        taskService.delegateTask(taskId, delegateToUser);
        log.info("任务委派: taskId={}, from={}, to={}", taskId, fromUserId, delegateToUser);
    }

    @Override
    public List<ApprovalRecordVO> getRecords(String businessType, Long businessId) {
        List<ApApprovalRecord> records = recordMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ApApprovalRecord>()
                .eq(ApApprovalRecord::getBusinessType, businessType)
                .eq(ApApprovalRecord::getBusinessId, businessId)
                .orderByDesc(ApApprovalRecord::getActionTime));
        List<ApprovalRecordVO> vos = new ArrayList<>();
        for (ApApprovalRecord r : records) {
            ApprovalRecordVO vo = new ApprovalRecordVO();
            BeanUtils.copyProperties(r, vo);
            vos.add(vo);
        }
        return vos;
    }

    private Long getCurrentTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof Long) {
            return (Long) auth.getDetails();
        }
        return 0L;
    }
}
