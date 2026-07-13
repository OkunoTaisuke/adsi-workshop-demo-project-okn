package com.example.attendance.leave.dto;

import com.example.attendance.leave.domain.LeaveType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record LeaveRequestCreateRequest(
    @NotNull LocalDate targetDate,
    @NotNull LeaveType leaveType,
    @Min(1) @Max(7) Integer hours,
    @NotNull @Size(min = 1, max = 500) String reason
) {}
