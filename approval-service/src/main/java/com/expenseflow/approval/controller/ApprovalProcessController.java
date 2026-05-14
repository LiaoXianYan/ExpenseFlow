package com.expenseflow.approval.controller;

import com.expenseflow.approval.BaseController;
import com.expenseflow.approval.dto.ApprovalStartDTO;
import com.expenseflow.approval.dto.ProcessStartResponse;
import com.expenseflow.approval.service.ApprovalProcessService;
import com.expenseflow.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/approval/process")
@RequiredArgsConstructor
public class ApprovalProcessController extends BaseController {

    private final ApprovalProcessService processService;

    @PostMapping("/start")
    public Result<ProcessStartResponse> startProcess(@Valid @RequestBody ApprovalStartDTO dto) {
        ProcessStartResponse response = processService.startProcess(dto);
        return Result.ok(response);
    }

    // Feign 内部回调地址，放行在 SecurityConfig 中
    @PutMapping("/callback/result")
    public Result<Void> callbackResult(@RequestParam String businessType,
                                       @RequestParam Long businessId,
                                       @RequestParam String outcome) {
        return Result.ok();
    }
}
