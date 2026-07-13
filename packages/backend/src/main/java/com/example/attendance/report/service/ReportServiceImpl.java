package com.example.attendance.report.service;

import com.example.attendance.attendance.domain.WorkDuration;
import com.example.attendance.attendance.entity.AttendanceRecord;
import com.example.attendance.attendance.repository.AttendanceRecordRepository;
import com.example.attendance.employee.entity.Employee;
import com.example.attendance.employee.repository.EmployeeRepository;
import com.example.attendance.leave.domain.LeaveBalance;
import com.example.attendance.leave.domain.LeaveRequestStatus;
import com.example.attendance.leave.domain.LeaveType;
import com.example.attendance.leave.entity.LeaveRequest;
import com.example.attendance.leave.repository.LeaveRequestRepository;
import com.example.attendance.leave.service.LeaveGrantService;
import com.example.attendance.report.dto.EmployeeMonthlyRecord;
import com.example.attendance.report.dto.MonthlyReportResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    private static final int STANDARD_WORK_HOURS_PER_DAY = 8;

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveGrantService leaveGrantService;

    public ReportServiceImpl(
            AttendanceRecordRepository attendanceRecordRepository,
            EmployeeRepository employeeRepository,
            LeaveRequestRepository leaveRequestRepository,
            LeaveGrantService leaveGrantService) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveGrantService = leaveGrantService;
    }

    @Override
    public MonthlyReportResponse getMonthlyReport(String month, UUID departmentId) {
        var yearMonth = YearMonth.parse(month);
        var start = yearMonth.atDay(1);
        var end = yearMonth.atEndOfMonth();

        List<Employee> employees;
        if (departmentId != null) {
            employees = employeeRepository.findByDepartmentId(departmentId);
        } else {
            employees = employeeRepository.findAll();
        }

        var employeeIds = employees.stream().map(Employee::getId).toList();
        var allRecords = attendanceRecordRepository
                .findByEmployeeIdInAndWorkDateBetween(employeeIds, start, end);
        var byEmployee = allRecords.stream()
                .collect(Collectors.groupingBy(r -> r.getEmployee().getId()));

        int weekdaysInMonth = countWeekdays(yearMonth);

        var approvedLeaves = leaveRequestRepository
                .findByRequesterIdInAndStatusAndTargetDateBetween(
                    employeeIds, LeaveRequestStatus.APPROVED, start, end);
        var leavesByEmployee = approvedLeaves.stream()
                .collect(Collectors.groupingBy(lr -> lr.getRequester().getId()));

        var records = employees.stream()
                .map(emp -> {
                    var empRecords = byEmployee.getOrDefault(emp.getId(), List.of());
                    var grouped = empRecords.stream()
                            .collect(Collectors.groupingBy(AttendanceRecord::getWorkDate));

                    int workDays = grouped.size();
                    int totalWorkMinutes = 0;
                    int totalOvertimeMinutes = 0;
                    for (var dayRecords : grouped.values()) {
                        var duration = WorkDuration.calculate(dayRecords);
                        totalWorkMinutes += duration.workMinutes();
                        totalOvertimeMinutes += duration.overtimeMinutes();
                    }

                    var empLeaves = leavesByEmployee.getOrDefault(emp.getId(), List.of());
                    BigDecimal paidLeaveDays = calculatePaidLeaveDays(empLeaves);
                    int fullDayLeaves = countFullDayLeaves(empLeaves);
                    int absentDays = Math.max(0, weekdaysInMonth - workDays - fullDayLeaves);

                    BigDecimal remainingLeaveDays = BigDecimal.ZERO;
                    try {
                        LeaveBalance balance = leaveGrantService.getBalance(emp.getId());
                        remainingLeaveDays = balance.remainingDays();
                    } catch (Exception e) {
                        log.debug("有給残日数取得失敗: employeeId={}", emp.getId());
                    }

                    return new EmployeeMonthlyRecord(
                            emp.getId(),
                            emp.getName(),
                            emp.getDepartment().getName(),
                            workDays,
                            totalWorkMinutes,
                            totalOvertimeMinutes,
                            absentDays,
                            paidLeaveDays,
                            remainingLeaveDays
                    );
                })
                .toList();

        return new MonthlyReportResponse(month, List.copyOf(records));
    }

    private BigDecimal calculatePaidLeaveDays(List<LeaveRequest> approvedLeaves) {
        return approvedLeaves.stream()
            .map(lr -> switch (lr.getLeaveType()) {
                case FULL_DAY -> BigDecimal.ONE;
                case HALF_DAY -> new BigDecimal("0.5");
                case HOURLY -> new BigDecimal(lr.getHours()).divide(
                    BigDecimal.valueOf(STANDARD_WORK_HOURS_PER_DAY), 2, java.math.RoundingMode.HALF_UP);
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private int countFullDayLeaves(List<LeaveRequest> approvedLeaves) {
        return (int) approvedLeaves.stream()
            .filter(lr -> lr.getLeaveType() == LeaveType.FULL_DAY)
            .count();
    }

    private int countWeekdays(YearMonth yearMonth) {
        int count = 0;
        var date = yearMonth.atDay(1);
        var end = yearMonth.atEndOfMonth();
        while (!date.isAfter(end)) {
            var dow = date.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                count++;
            }
            date = date.plusDays(1);
        }
        return count;
    }
}
