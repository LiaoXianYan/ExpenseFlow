package com.expenseflow.approval.service;

import com.expenseflow.approval.dto.TaskCompleteDTO;
import com.expenseflow.approval.entity.ApApprovalRecord;
import com.expenseflow.approval.feign.SystemFeignClient;
import com.expenseflow.approval.mapper.ApApprovalRecordMapper;
import com.expenseflow.approval.service.impl.ApprovalTaskServiceImpl;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApprovalTaskServiceImplTest {

    @Mock TaskService taskService;
    @Mock ApApprovalRecordMapper recordMapper;
    @Mock SystemFeignClient systemFeignClient;
    @InjectMocks ApprovalTaskServiceImpl taskServiceImpl;

    private Task mockTask(String id, String piId, String name, String assignee) {
        Task task = mock(Task.class);
        lenient().when(task.getId()).thenReturn(id);
        lenient().when(task.getProcessInstanceId()).thenReturn(piId);
        lenient().when(task.getName()).thenReturn(name);
        lenient().when(task.getAssignee()).thenReturn(assignee);
        return task;
    }

    private void mockTaskQuery(String taskId, Task result) {
        TaskQuery query = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskId(taskId)).thenReturn(query);
        when(query.singleResult()).thenReturn(result);
    }

    @Test
    @DisplayName("completeTask: 任务不存在抛异常")
    void shouldThrowWhenTaskNotFound() {
        mockTaskQuery("bad-id", null);

        TaskCompleteDTO dto = new TaskCompleteDTO();
        dto.setAction("APPROVE");
        assertThrows(IllegalArgumentException.class,
            () -> taskServiceImpl.completeTask("bad-id", 1L, "user", dto));
    }

    @Test
    @DisplayName("completeTask: 审批通过完成任务")
    void shouldCompleteTaskAndRecord() {
        Task task = mockTask("task-1", "pi-1", "经理审批", "2");
        mockTaskQuery("task-1", task);
        when(taskService.getVariable("task-1", "businessType")).thenReturn("TRAVEL_REQUEST");
        when(taskService.getVariable("task-1", "businessId")).thenReturn(1L);
        lenient().when(recordMapper.insert(any(ApApprovalRecord.class))).thenReturn(1);

        TaskCompleteDTO dto = new TaskCompleteDTO();
        dto.setAction("APPROVE");
        dto.setComment("同意");
        taskServiceImpl.completeTask("task-1", 1L, "张三", dto);

        verify(taskService).complete(eq("task-1"), anyMap());
    }

    @Test
    @DisplayName("completeTask: 未认领任务先认领")
    void shouldClaimBeforeCompleteWhenUnassigned() {
        Task task = mockTask("task-2", "pi-2", "总监审批", null);
        mockTaskQuery("task-2", task);
        when(taskService.getVariable("task-2", "businessType")).thenReturn("EXPENSE_REPORT");
        when(taskService.getVariable("task-2", "businessId")).thenReturn(2L);
        lenient().when(recordMapper.insert(any(ApApprovalRecord.class))).thenReturn(1);

        TaskCompleteDTO dto = new TaskCompleteDTO();
        dto.setAction("REJECT");
        dto.setComment("金额超标");
        taskServiceImpl.completeTask("task-2", 1L, "李四", dto);

        verify(taskService).claim("task-2", "1");
        verify(taskService).complete(eq("task-2"), anyMap());
    }

    @Test
    @DisplayName("delegateTask: 委派成功")
    void shouldDelegateTask() {
        Task task = mockTask("task-3", "pi-3", "经理审批", "1");
        mockTaskQuery("task-3", task);
        when(taskService.getVariable("task-3", "businessType")).thenReturn("TRAVEL_REQUEST");
        when(taskService.getVariable("task-3", "businessId")).thenReturn(3L);
        lenient().when(recordMapper.insert(any(ApApprovalRecord.class))).thenReturn(1);

        taskServiceImpl.delegateTask("task-3", 1L, "王五");
        verify(taskService).delegateTask("task-3", "王五");
    }

    @Test
    @DisplayName("getRecords: 返回审批记录列表")
    void shouldReturnApprovalRecords() {
        when(recordMapper.selectList(any())).thenReturn(java.util.Collections.emptyList());

        var records = taskServiceImpl.getRecords("TRAVEL_REQUEST", 1L);
        assertThat(records).isNotNull();
    }
}
