package com.example.attendance.leave.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LeaveRejectRequest(
    @NotNull @Size(min = 1, max = 500) String reason
) {}
