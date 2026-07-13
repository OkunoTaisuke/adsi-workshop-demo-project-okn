package com.example.attendance.leave.dto;

import com.example.attendance.leave.domain.LeaveRequestStatus;
import com.example.attendance.leave.domain.LeaveType;
import com.example.attendance.leave.entity.LeaveRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LeavePendingResponse(
    UUID id,
    UUID requesterId,
    String requesterName,
    LocalDate targetDate,
    LeaveType leaveType,
    Integer hours,
    String reason,
    LeaveRequestStatus status,
    Instant createdAt
) {
    public static LeavePendingResponse from(LeaveRequest entity) {
        return new LeavePendingResponse(
            entity.getId(),
            entity.getRequester().getId(),
            entity.getRequester().getName(),
            entity.getTargetDate(),
            entity.getLeaveType(),
            entity.getHours(),
            entity.getReason(),
            entity.getStatus(),
            entity.getCreatedAt()
        );
    }
}
