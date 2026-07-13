package com.example.attendance.leave.service;

import com.example.attendance.leave.domain.LeaveBalance;

import java.math.BigDecimal;
import java.util.UUID;

public interface LeaveGrantService {

    LeaveBalance getBalance(UUID employeeId);

    void consumeDays(UUID employeeId, BigDecimal days);

    void restoreDays(UUID employeeId, BigDecimal days);
}
