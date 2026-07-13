package com.example.attendance.leave.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LeaveGrantDetailResponse(
    int fiscalYear,
    LocalDate grantDate,
    BigDecimal grantedDays,
    BigDecimal usedDays,
    BigDecimal remainingDays,
    LocalDate expiryDate,
    boolean isCarriedOver
) {}
