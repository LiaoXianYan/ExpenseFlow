package com.expenseflow.approval.service;

import com.expenseflow.approval.dto.ApprovalStartDTO;
import com.expenseflow.approval.dto.ProcessStartResponse;

public interface ApprovalProcessService {
    ProcessStartResponse startProcess(ApprovalStartDTO dto);
}
