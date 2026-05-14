package com.expenseflow.approval.controller;

import com.expenseflow.approval.BaseController;
import com.expenseflow.approval.dto.TaskCompleteDTO;
import com.expenseflow.approval.service.ApprovalTaskService;
import com.expenseflow.approval.vo.ApprovalRecordVO;
import com.expenseflow.approval.vo.ApprovalTaskVO;
import com.expenseflow.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/approval/task")
@RequiredArgsConstructor
public class ApprovalTaskController extends BaseController {

    private final ApprovalTaskService taskService;

    @GetMapping("/page")
    public Result<List<ApprovalTaskVO>> page(
            @RequestParam(required = false) String candidateGroup) {
        List<ApprovalTaskVO> tasks;
        if (candidateGroup != null && !candidateGroup.isEmpty()) {
            tasks = taskService.listTasks(candidateGroup);
        } else {
            Long userId = getCurrentUserId();
            tasks = taskService.listTasksByAssignee(userId);
        }
        return Result.ok(tasks);
    }

    @PostMapping("/{taskId}/complete")
    public Result<Void> complete(@PathVariable String taskId,
                                  @Valid @RequestBody TaskCompleteDTO dto) {
        Long approverId = getCurrentUserId();
        taskService.completeTask(taskId, approverId, null, dto);
        return Result.ok();
    }

    @PostMapping("/{taskId}/delegate")
    public Result<Void> delegate(@PathVariable String taskId,
                                  @RequestParam String delegateToUser) {
        Long fromUserId = getCurrentUserId();
        taskService.delegateTask(taskId, fromUserId, delegateToUser);
        return Result.ok();
    }

    @GetMapping("/record/list")
    public Result<List<ApprovalRecordVO>> records(@RequestParam String businessType,
                                                   @RequestParam Long businessId) {
        return Result.ok(taskService.getRecords(businessType, businessId));
    }
}
