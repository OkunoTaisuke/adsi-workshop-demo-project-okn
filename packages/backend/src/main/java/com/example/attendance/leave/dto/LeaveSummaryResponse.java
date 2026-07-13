package com.example.attendance.leave.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record LeaveSummaryResponse(
    int fiscalYear,
    List<EmployeeLeaveSummary> employees
) {
    public record EmployeeLeaveSummary(
        UUID employeeId,
        String employeeName,
        String departmentName,
        BigDecimal grantedDays,
        BigDecimal usedDays,
        BigDecimal remainingDays,
        int hourlyUsedHours
    ) {}
}
