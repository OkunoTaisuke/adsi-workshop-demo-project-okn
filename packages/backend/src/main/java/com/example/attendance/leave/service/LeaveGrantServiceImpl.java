package com.example.attendance.leave.service;

import com.example.attendance.employee.entity.Employee;
import com.example.attendance.employee.repository.EmployeeRepository;
import com.example.attendance.leave.domain.LeaveBalance;
import com.example.attendance.leave.domain.LeaveGrantCalculator;
import com.example.attendance.leave.entity.LeaveGrant;
import com.example.attendance.leave.repository.LeaveGrantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class LeaveGrantServiceImpl implements LeaveGrantService {

    private final LeaveGrantRepository leaveGrantRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveGrantCalculator calculator;
    private final Clock clock;

    public LeaveGrantServiceImpl(LeaveGrantRepository leaveGrantRepository,
                                  EmployeeRepository employeeRepository,
                                  LeaveGrantCalculator calculator,
                                  Clock clock) {
        this.leaveGrantRepository = leaveGrantRepository;
        this.employeeRepository = employeeRepository;
        this.calculator = calculator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public LeaveBalance getBalance(UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new EntityNotFoundException("社員が見つかりません: " + employeeId));

        LocalDate today = LocalDate.now(clock);
        ensureGrantsGenerated(employee, today);

        List<LeaveGrant> validGrants = leaveGrantRepository
            .findByEmployeeIdAndExpiryDateAfterOrderByExpiryDateAsc(employeeId, today);

        int currentFiscalYear = calculator.determineFiscalYear(today);

        BigDecimal totalGrantedDays = BigDecimal.ZERO;
        BigDecimal totalUsedDays = BigDecimal.ZERO;
        BigDecimal currentYearGrantDays = BigDecimal.ZERO;
        BigDecimal carriedOverDays = BigDecimal.ZERO;
        int hourlyUsedHours = 0;

        List<LeaveBalance.GrantDetail> grantDetails = new ArrayList<>();

        for (LeaveGrant grant : validGrants) {
            BigDecimal remaining = grant.getRemainingDays();
            totalGrantedDays = totalGrantedDays.add(grant.getGrantedDays());
            totalUsedDays = totalUsedDays.add(grant.getUsedDays());

            boolean isCarriedOver = grant.getFiscalYear() < currentFiscalYear;
            if (isCarriedOver) {
                carriedOverDays = carriedOverDays.add(remaining);
            } else {
                currentYearGrantDays = currentYearGrantDays.add(grant.getGrantedDays());
            }

            grantDetails.add(new LeaveBalance.GrantDetail(
                grant.getFiscalYear(),
                grant.getGrantDate(),
                grant.getGrantedDays(),
                grant.getUsedDays(),
                remaining,
                grant.getExpiryDate(),
                isCarriedOver
            ));
        }

        BigDecimal remainingDays = totalGrantedDays.subtract(totalUsedDays);
        int hourlyRemainingHours = 40 - hourlyUsedHours;

        return new LeaveBalance(
            currentFiscalYear,
            totalGrantedDays,
            totalUsedDays,
            remainingDays,
            hourlyUsedHours,
            hourlyRemainingHours,
            currentYearGrantDays,
            carriedOverDays,
            grantDetails
        );
    }

    @Override
    @Transactional
    public void consumeDays(UUID employeeId, BigDecimal days) {
        LocalDate today = LocalDate.now(clock);
        List<LeaveGrant> validGrants = leaveGrantRepository
            .findByEmployeeIdAndExpiryDateAfterOrderByExpiryDateAsc(employeeId, today);

        BigDecimal remaining = days;
        for (LeaveGrant grant : validGrants) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal available = grant.getGrantedDays().subtract(grant.getUsedDays());
            BigDecimal toConsume = remaining.min(available);
            grant.setUsedDays(grant.getUsedDays().add(toConsume));
            leaveGrantRepository.save(grant);
            remaining = remaining.subtract(toConsume);
        }
    }

    @Override
    @Transactional
    public void restoreDays(UUID employeeId, BigDecimal days) {
        LocalDate today = LocalDate.now(clock);
        List<LeaveGrant> validGrants = leaveGrantRepository
            .findByEmployeeIdAndExpiryDateAfterOrderByExpiryDateAsc(employeeId, today);

        // Restore from newest first (reverse order)
        BigDecimal remaining = days;
        for (int i = validGrants.size() - 1; i >= 0; i--) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            LeaveGrant grant = validGrants.get(i);
            BigDecimal toRestore = remaining.min(grant.getUsedDays());
            grant.setUsedDays(grant.getUsedDays().subtract(toRestore));
            leaveGrantRepository.save(grant);
            remaining = remaining.subtract(toRestore);
        }
    }

    private void ensureGrantsGenerated(Employee employee, LocalDate today) {
        List<LocalDate> grantDates = calculator.calculateGrantDatesUpTo(employee.getHireDate(), today);
        List<LeaveGrant> toSave = new ArrayList<>();

        for (LocalDate grantDate : grantDates) {
            boolean exists = leaveGrantRepository
                .findByEmployeeIdAndGrantDate(employee.getId(), grantDate)
                .isPresent();

            if (!exists) {
                double tenure = calculator.calculateTenureYears(employee.getHireDate(), grantDate);
                BigDecimal days = calculator.calculateGrantDays(tenure);
                int fiscalYear = calculator.determineFiscalYear(grantDate);
                LocalDate expiryDate = calculator.calculateExpiryDate(grantDate);

                LeaveGrant grant = LeaveGrant.builder()
                    .id(UUID.randomUUID())
                    .employee(employee)
                    .fiscalYear(fiscalYear)
                    .grantDate(grantDate)
                    .grantedDays(days)
                    .usedDays(BigDecimal.ZERO)
                    .expiryDate(expiryDate)
                    .build();
                toSave.add(grant);
            }
        }

        if (!toSave.isEmpty()) {
            leaveGrantRepository.saveAll(toSave);
        }
    }
}
