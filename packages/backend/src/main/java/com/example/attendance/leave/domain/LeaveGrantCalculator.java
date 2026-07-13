package com.example.attendance.leave.domain;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
public class LeaveGrantCalculator {

    private static final BigDecimal[] GRANT_TABLE = {
        BigDecimal.valueOf(10),  // 0.5年
        BigDecimal.valueOf(11),  // 1.5年
        BigDecimal.valueOf(12),  // 2.5年
        BigDecimal.valueOf(14),  // 3.5年
        BigDecimal.valueOf(16),  // 4.5年
        BigDecimal.valueOf(18),  // 5.5年
        BigDecimal.valueOf(20),  // 6.5年以上
    };

    public BigDecimal calculateGrantDays(double tenureYears) {
        int index = (int) Math.round(tenureYears - 0.5);
        if (index < 0) {
            return BigDecimal.ZERO;
        }
        if (index >= GRANT_TABLE.length) {
            return GRANT_TABLE[GRANT_TABLE.length - 1];
        }
        return GRANT_TABLE[index];
    }

    public List<LocalDate> calculateGrantDatesUpTo(LocalDate hireDate, LocalDate asOf) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate firstGrant = hireDate.plusMonths(6);
        if (firstGrant.isAfter(asOf)) {
            return dates;
        }
        dates.add(firstGrant);

        LocalDate next = firstGrant.plusYears(1);
        while (!next.isAfter(asOf)) {
            dates.add(next);
            next = next.plusYears(1);
        }
        return dates;
    }

    public double calculateTenureYears(LocalDate hireDate, LocalDate grantDate) {
        long months = ChronoUnit.MONTHS.between(hireDate, grantDate);
        return months / 12.0;
    }

    public int determineFiscalYear(LocalDate date) {
        if (date.getMonthValue() >= 4) {
            return date.getYear();
        }
        return date.getYear() - 1;
    }

    public LocalDate calculateExpiryDate(LocalDate grantDate) {
        int fiscalYear = determineFiscalYear(grantDate);
        int expiryFiscalYear = fiscalYear + 2;
        return LocalDate.of(expiryFiscalYear + 1, 3, 31);
    }
}
