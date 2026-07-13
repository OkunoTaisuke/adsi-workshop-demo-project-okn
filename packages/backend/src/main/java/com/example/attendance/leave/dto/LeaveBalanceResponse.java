package com.example.attendance.leave.dto;

import com.example.attendance.leave.domain.LeaveBalance;

import java.math.BigDecimal;
import java.util.List;

public record LeaveBalanceResponse(
    int fiscalYear,
    BigDecimal totalGrantedDays,
    BigDecimal usedDays,
    BigDecimal remainingDays,
    int hourlyUsedHours,
    int hourlyRemainingHours,
    List<LeaveGrantDetailResponse> grants
) {
    public static LeaveBalanceResponse from(LeaveBalance balance) {
        List<LeaveGrantDetailResponse> grantDetails = balance.grants().stream()
            .map(g -> new LeaveGrantDetailResponse(
                g.fiscalYear(),
                g.grantDate(),
                g.grantedDays(),
                g.usedDays(),
                g.remainingDays(),
                g.expiryDate(),
                g.isCarriedOver()
            ))
            .toList();

        return new LeaveBalanceResponse(
            balance.fiscalYear(),
            balance.totalGrantedDays(),
            balance.usedDays(),
            balance.remainingDays(),
            balance.hourlyUsedHours(),
            balance.hourlyRemainingHours(),
            grantDetails
        );
    }
}
