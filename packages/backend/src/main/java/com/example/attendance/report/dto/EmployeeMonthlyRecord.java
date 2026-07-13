package com.example.attendance.report.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record EmployeeMonthlyRecord(
    UUID employeeId,
    String employeeName,
    String departmentName,
    int workDays,
    int totalWorkMinutes,
    int totalOvertimeMinutes,
    int absentDays,
    BigDecimal paidLeaveDays,
    BigDecimal remainingLeaveDays
) {}
