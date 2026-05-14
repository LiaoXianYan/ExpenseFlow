package com.expenseflow.approval.service;

import com.expenseflow.approval.dto.TaskCompleteDTO;
import com.expenseflow.approval.vo.ApprovalRecordVO;
import com.expenseflow.approval.vo.ApprovalTaskVO;

import java.util.List;

public interface ApprovalTaskService {
    List<ApprovalTaskVO> listTasks(String candidateGroup);
    List<ApprovalTaskVO> listTasksByAssignee(Long userId);
    void completeTask(String taskId, Long approverId, String approverName, TaskCompleteDTO dto);
    void delegateTask(String taskId, Long fromUserId, String delegateToUser);
    List<ApprovalRecordVO> getRecords(String businessType, Long businessId);
}
