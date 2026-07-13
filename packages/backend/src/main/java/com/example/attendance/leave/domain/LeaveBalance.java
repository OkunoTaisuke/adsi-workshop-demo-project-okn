package com.example.attendance.leave.domain;

import java.math.BigDecimal;
import java.util.List;

public record LeaveBalance(
    int fiscalYear,
    BigDecimal totalGrantedDays,
    BigDecimal usedDays,
    BigDecimal remainingDays,
    int hourlyUsedHours,
    int hourlyRemainingHours,
    BigDecimal currentYearGrantDays,
    BigDecimal carriedOverDays,
    List<GrantDetail> grants
) {
    public LeaveBalance {
        grants = List.copyOf(grants);
    }

    public record GrantDetail(
        int fiscalYear,
        java.time.LocalDate grantDate,
        BigDecimal grantedDays,
        BigDecimal usedDays,
        BigDecimal remainingDays,
        java.time.LocalDate expiryDate,
        boolean isCarriedOver
    ) {}
}
