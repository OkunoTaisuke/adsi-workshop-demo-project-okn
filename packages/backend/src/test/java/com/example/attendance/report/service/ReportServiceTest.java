package com.example.attendance.report.service;

import com.example.attendance.attendance.domain.WorkDuration;
import com.example.attendance.attendance.entity.AttendanceRecord;
import com.example.attendance.attendance.repository.AttendanceRecordRepository;
import com.example.attendance.department.entity.Department;
import com.example.attendance.employee.entity.Employee;
import com.example.attendance.employee.entity.Role;
import com.example.attendance.employee.repository.EmployeeRepository;
import com.example.attendance.leave.domain.LeaveBalance;
import com.example.attendance.leave.repository.LeaveRequestRepository;
import com.example.attendance.leave.service.LeaveGrantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServiceTest {

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private LeaveGrantService leaveGrantService;

    private ReportServiceImpl service;

    private Department engineering;
    private Department sales;
    private Employee tanaka;
    private Employee suzuki;

    @BeforeEach
    void setUp() {
        service = new ReportServiceImpl(attendanceRecordRepository, employeeRepository, leaveRequestRepository, leaveGrantService);

        when(leaveRequestRepository.findByRequesterIdAndStatusOrderByCreatedAtDesc(any(), any()))
            .thenReturn(List.of());
        when(leaveGrantService.getBalance(any())).thenReturn(new LeaveBalance(
            2025, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, 0, 40,
            BigDecimal.TEN, BigDecimal.ZERO, List.of()));

        engineering = Department.builder()
                .id(UUID.randomUUID())
                .name("Engineering")
                .build();
        sales = Department.builder()
                .id(UUID.randomUUID())
                .name("Sales")
                .build();

        tanaka = Employee.builder()
                .id(UUID.randomUUID())
                .name("田中太郎")
                .email("tanaka@example.com")
                .password("hashed")
                .department(engineering)
                .role(Role.EMPLOYEE)
                .isManager(false)
                .hireDate(LocalDate.of(2024, 4, 1))
                .build();

        suzuki = Employee.builder()
                .id(UUID.randomUUID())
                .name("鈴木花子")
                .email("suzuki@example.com")
                .password("hashed")
                .department(sales)
                .role(Role.EMPLOYEE)
                .isManager(false)
                .hireDate(LocalDate.of(2024, 4, 1))
                .build();
    }

    @Test
    @DisplayName("全社員の月次集計が正しく計算される")
    void getMonthlyReport_allEmployees_returnsCorrectSummary() {
        // Arrange
        var record1 = AttendanceRecord.builder()
                .id(UUID.randomUUID())
                .employee(tanaka)
                .workDate(LocalDate.of(2025, 1, 15))
                .clockIn(Instant.parse("2025-01-14T23:00:00Z"))
                .clockOut(Instant.parse("2025-01-15T08:00:00Z"))
                .build();
        var record2 = AttendanceRecord.builder()
                .id(UUID.randomUUID())
                .employee(tanaka)
                .workDate(LocalDate.of(2025, 1, 16))
                .clockIn(Instant.parse("2025-01-15T23:00:00Z"))
                .clockOut(Instant.parse("2025-01-16T08:00:00Z"))
                .build();

        when(employeeRepository.findAll()).thenReturn(List.of(tanaka, suzuki));
        when(attendanceRecordRepository.findByEmployeeIdInAndWorkDateBetween(
                any(), eq(LocalDate.of(2025, 1, 1)), eq(LocalDate.of(2025, 1, 31))))
                .thenReturn(List.of(record1, record2));

        // Act
        var result = service.getMonthlyReport("2025-01", null);

        // Assert
        assertThat(result.month()).isEqualTo("2025-01");
        assertThat(result.records()).hasSize(2);

        var tanakaRecord = result.records().stream()
                .filter(r -> r.employeeId().equals(tanaka.getId()))
                .findFirst().orElseThrow();
        assertThat(tanakaRecord.workDays()).isEqualTo(2);
        assertThat(tanakaRecord.departmentName()).isEqualTo("Engineering");

        var suzukiRecord = result.records().stream()
                .filter(r -> r.employeeId().equals(suzuki.getId()))
                .findFirst().orElseThrow();
        assertThat(suzukiRecord.workDays()).isEqualTo(0);
    }

    @Test
    @DisplayName("部署フィルタで指定部署のみ集計される")
    void getMonthlyReport_withDepartmentFilter_returnsFilteredResults() {
        // Arrange
        when(employeeRepository.findByDepartmentId(engineering.getId()))
                .thenReturn(List.of(tanaka));
        when(attendanceRecordRepository.findByEmployeeIdInAndWorkDateBetween(
                any(), eq(LocalDate.of(2025, 1, 1)), eq(LocalDate.of(2025, 1, 31))))
                .thenReturn(List.of());

        // Act
        var result = service.getMonthlyReport("2025-01", engineering.getId());

        // Assert
        assertThat(result.records()).hasSize(1);
        assertThat(result.records().get(0).employeeName()).isEqualTo("田中太郎");
    }
}
