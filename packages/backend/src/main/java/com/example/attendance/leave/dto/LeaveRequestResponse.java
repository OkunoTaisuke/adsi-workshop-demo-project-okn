package com.example.attendance.leave.dto;

import com.example.attendance.leave.domain.LeaveRequestStatus;
import com.example.attendance.leave.domain.LeaveType;
import com.example.attendance.leave.entity.LeaveRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LeaveRequestResponse(
    UUID id,
    LocalDate targetDate,
    LeaveType leaveType,
    Integer hours,
    String reason,
    LeaveRequestStatus status,
    String approverName,
    String rejectReason,
    Instant createdAt
) {
    public static LeaveRequestResponse from(LeaveRequest entity) {
        return new LeaveRequestResponse(
            entity.getId(),
            entity.getTargetDate(),
            entity.getLeaveType(),
            entity.getHours(),
            entity.getReason(),
            entity.getStatus(),
            entity.getApprover() != null ? entity.getApprover().getName() : null,
            entity.getRejectReason(),
            entity.getCreatedAt()
        );
    }
}
