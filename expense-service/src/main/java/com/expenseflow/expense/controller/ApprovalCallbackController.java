package com.expenseflow.expense.controller;

import com.expenseflow.expense.entity.ExExpenseReport;
import com.expenseflow.expense.entity.ExTravelRequest;
import com.expenseflow.expense.mapper.ExExpenseReportMapper;
import com.expenseflow.expense.mapper.ExTravelRequestMapper;
import com.expenseflow.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/expense/callback")
@RequiredArgsConstructor
public class ApprovalCallbackController {

    private final ExTravelRequestMapper travelMapper;
    private final ExExpenseReportMapper reportMapper;

    @PutMapping("/approval-result")
    public Result<Void> updateApprovalResult(@RequestBody Map<String, Object> body) {
        String businessType = (String) body.get("businessType");
        Long businessId = longValue(body.get("businessId"));
        String outcome = (String) body.get("outcome");

        log.info("审批回调: businessType={}, businessId={}, outcome={}", businessType, businessId, outcome);

        String status = "REJECTED".equals(outcome) ? "REJECTED" : "APPROVED";

        if ("TRAVEL_REQUEST".equals(businessType)) {
            ExTravelRequest t = travelMapper.selectById(businessId);
            if (t == null) return Result.fail(404, "出差申请不存在");
            t.setStatus(status);
            travelMapper.updateById(t);
        } else if ("EXPENSE_REPORT".equals(businessType)) {
            ExExpenseReport r = reportMapper.selectById(businessId);
            if (r == null) return Result.fail(404, "报销单不存在");
            r.setStatus(status);
            reportMapper.updateById(r);
        } else {
            return Result.fail(400, "未知业务类型: " + businessType);
        }

        log.info("审批结果更新完成: businessId={}, status={}", businessId, status);
        return Result.ok();
    }

    private Long longValue(Object obj) {
        if (obj instanceof Number) return ((Number) obj).longValue();
        return null;
    }
}
