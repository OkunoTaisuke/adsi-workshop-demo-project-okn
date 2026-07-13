package com.example.attendance.leave.service;

import com.example.attendance.leave.dto.LeaveRequestCreateRequest;
import com.example.attendance.leave.dto.LeaveRequestResponse;
import com.example.attendance.leave.dto.LeavePendingResponse;
import com.example.attendance.leave.dto.LeaveSummaryResponse;

import java.util.List;
import java.util.UUID;

public interface LeaveService {

    LeaveRequestResponse createRequest(UUID requesterId, LeaveRequestCreateRequest request);

    List<LeaveRequestResponse> getMyRequests(UUID requesterId, String status);

    List<LeavePendingResponse> getPendingRequests(UUID approverId);

    LeaveRequestResponse approve(UUID requestId, UUID approverId);

    LeaveRequestResponse reject(UUID requestId, UUID approverId, String reason);

    LeaveRequestResponse withdraw(UUID requestId, UUID requesterId);

    LeaveSummaryResponse getSummary(Integer fiscalYear, UUID departmentId);
}
