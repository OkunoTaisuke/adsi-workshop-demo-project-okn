package com.example.attendance.leave.service;

import com.example.attendance.department.entity.Department;
import com.example.attendance.employee.entity.Employee;
import com.example.attendance.employee.entity.Role;
import com.example.attendance.employee.repository.EmployeeRepository;
import com.example.attendance.leave.domain.LeaveBalance;
import com.example.attendance.leave.domain.LeaveGrantCalculator;
import com.example.attendance.leave.domain.LeaveRequestStatus;
import com.example.attendance.leave.domain.LeaveType;
import com.example.attendance.leave.dto.LeaveRequestCreateRequest;
import com.example.attendance.leave.dto.LeaveRequestResponse;
import com.example.attendance.leave.entity.LeaveRequest;
import com.example.attendance.leave.repository.LeaveRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeaveServiceTest {

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private LeaveGrantService leaveGrantService;

    @Mock
    private EmployeeRepository employeeRepository;

    private LeaveServiceImpl service;

    private Clock fixedClock;
    private Department department;
    private Employee employee;
    private Employee manager;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(
            ZonedDateTime.of(2026, 7, 13, 9, 0, 0, 0, ZoneId.of("Asia/Tokyo")).toInstant(),
            ZoneId.of("Asia/Tokyo")
        );
        service = new LeaveServiceImpl(leaveRequestRepository, leaveGrantService, employeeRepository, new LeaveGrantCalculator(), fixedClock);

        department = Department.builder()
            .id(UUID.randomUUID())
            .name("開発部")
            .build();

        employee = Employee.builder()
            .id(UUID.randomUUID())
            .name("田中太郎")
            .email("tanaka@example.com")
            .department(department)
            .role(Role.EMPLOYEE)
            .isManager(false)
            .hireDate(LocalDate.of(2024, 4, 1))
            .build();

        manager = Employee.builder()
            .id(UUID.randomUUID())
            .name("鈴木部長")
            .email("suzuki@example.com")
            .department(department)
            .role(Role.EMPLOYEE)
            .isManager(true)
            .hireDate(LocalDate.of(2020, 4, 1))
            .build();
    }

    private LeaveBalance makeBalance(BigDecimal remaining) {
        return new LeaveBalance(2026, BigDecimal.valueOf(20), BigDecimal.valueOf(20).subtract(remaining),
            remaining, 0, 40, BigDecimal.valueOf(20), BigDecimal.ZERO, List.of());
    }

    @Test
    @DisplayName("全日有給申請が正しく作成される")
    void createRequest_fullDay_success() {
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(leaveGrantService.getBalance(employee.getId())).thenReturn(makeBalance(BigDecimal.TEN));
        when(leaveRequestRepository.findByRequesterIdAndTargetDateAndStatusIn(any(), any(), any()))
            .thenReturn(List.of());
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new LeaveRequestCreateRequest(LocalDate.of(2026, 8, 1), LeaveType.FULL_DAY, null, "私用のため");
        LeaveRequestResponse response = service.createRequest(employee.getId(), request);

        assertThat(response.status()).isEqualTo(LeaveRequestStatus.PENDING);
        assertThat(response.leaveType()).isEqualTo(LeaveType.FULL_DAY);
        assertThat(response.targetDate()).isEqualTo(LocalDate.of(2026, 8, 1));
    }

    @Test
    @DisplayName("半日有給申請が正しく作成される")
    void createRequest_halfDay_success() {
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(leaveGrantService.getBalance(employee.getId())).thenReturn(makeBalance(BigDecimal.TEN));
        when(leaveRequestRepository.findByRequesterIdAndTargetDateAndStatusIn(any(), any(), any()))
            .thenReturn(List.of());
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new LeaveRequestCreateRequest(LocalDate.of(2026, 8, 1), LeaveType.HALF_DAY, null, "通院");
        LeaveRequestResponse response = service.createRequest(employee.getId(), request);

        assertThat(response.leaveType()).isEqualTo(LeaveType.HALF_DAY);
    }

    @Test
    @DisplayName("時間単位有給申請が正しく作成される")
    void createRequest_hourly_success() {
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(leaveGrantService.getBalance(employee.getId())).thenReturn(makeBalance(BigDecimal.TEN));
        when(leaveRequestRepository.findByRequesterIdAndTargetDateAndStatusIn(any(), any(), any()))
            .thenReturn(List.of());
        when(leaveRequestRepository.sumHoursByRequesterAndTypeAndStatusInRange(any(), any(), any(), any(), any()))
            .thenReturn(0);
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new LeaveRequestCreateRequest(LocalDate.of(2026, 8, 1), LeaveType.HOURLY, 3, "私用");
        LeaveRequestResponse response = service.createRequest(employee.getId(), request);

        assertThat(response.leaveType()).isEqualTo(LeaveType.HOURLY);
        assertThat(response.hours()).isEqualTo(3);
    }

    @Test
    @DisplayName("残日数不足で申請エラー")
    void createRequest_insufficientBalance_throws() {
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(leaveGrantService.getBalance(employee.getId())).thenReturn(makeBalance(BigDecimal.ZERO));
        when(leaveRequestRepository.findByRequesterIdAndTargetDateAndStatusIn(any(), any(), any()))
            .thenReturn(List.of());

        var request = new LeaveRequestCreateRequest(LocalDate.of(2026, 8, 1), LeaveType.FULL_DAY, null, "私用");

        assertThatThrownBy(() -> service.createRequest(employee.getId(), request))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("残日数");
    }

    @Test
    @DisplayName("時間単位年休の年間上限超過で申請エラー")
    void createRequest_hourlyLimitExceeded_throws() {
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(leaveGrantService.getBalance(employee.getId())).thenReturn(makeBalance(BigDecimal.TEN));
        when(leaveRequestRepository.findByRequesterIdAndTargetDateAndStatusIn(any(), any(), any()))
            .thenReturn(List.of());
        when(leaveRequestRepository.sumHoursByRequesterAndTypeAndStatusInRange(any(), any(), any(), any(), any()))
            .thenReturn(38);

        var request = new LeaveRequestCreateRequest(LocalDate.of(2026, 8, 1), LeaveType.HOURLY, 3, "私用");

        assertThatThrownBy(() -> service.createRequest(employee.getId(), request))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("時間単位");
    }

    @Test
    @DisplayName("同日に重複申請でエラー")
    void createRequest_duplicateDate_throws() {
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(leaveGrantService.getBalance(employee.getId())).thenReturn(makeBalance(BigDecimal.TEN));
        LeaveRequest existing = LeaveRequest.builder().id(UUID.randomUUID()).status(LeaveRequestStatus.PENDING).build();
        when(leaveRequestRepository.findByRequesterIdAndTargetDateAndStatusIn(any(), any(), any()))
            .thenReturn(List.of(existing));

        var request = new LeaveRequestCreateRequest(LocalDate.of(2026, 8, 1), LeaveType.FULL_DAY, null, "私用");

        assertThatThrownBy(() -> service.createRequest(employee.getId(), request))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("既に申請");
    }

    @Test
    @DisplayName("承認時にusedDaysが加算される")
    void approve_success_updatesUsedDays() {
        LeaveRequest pending = LeaveRequest.builder()
            .id(UUID.randomUUID())
            .requester(employee)
            .targetDate(LocalDate.of(2026, 8, 1))
            .leaveType(LeaveType.FULL_DAY)
            .hours(null)
            .reason("私用")
            .status(LeaveRequestStatus.PENDING)
            .version(0L)
            .build();

        when(leaveRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(employeeRepository.findById(manager.getId())).thenReturn(Optional.of(manager));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        LeaveRequestResponse response = service.approve(pending.getId(), manager.getId());

        assertThat(response.status()).isEqualTo(LeaveRequestStatus.APPROVED);
        verify(leaveGrantService).consumeDays(employee.getId(), BigDecimal.ONE);
    }

    @Test
    @DisplayName("却下時にusedDaysは変わらない")
    void reject_success_noUsedDaysChange() {
        LeaveRequest pending = LeaveRequest.builder()
            .id(UUID.randomUUID())
            .requester(employee)
            .targetDate(LocalDate.of(2026, 8, 1))
            .leaveType(LeaveType.FULL_DAY)
            .hours(null)
            .reason("私用")
            .status(LeaveRequestStatus.PENDING)
            .version(0L)
            .build();

        when(leaveRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(employeeRepository.findById(manager.getId())).thenReturn(Optional.of(manager));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        LeaveRequestResponse response = service.reject(pending.getId(), manager.getId(), "業務都合");

        assertThat(response.status()).isEqualTo(LeaveRequestStatus.REJECTED);
        verify(leaveGrantService, never()).consumeDays(any(), any());
    }

    @Test
    @DisplayName("PENDING取り下げ時にusedDaysは変わらない")
    void withdraw_pending_success_noUsedDaysChange() {
        LeaveRequest pending = LeaveRequest.builder()
            .id(UUID.randomUUID())
            .requester(employee)
            .targetDate(LocalDate.of(2026, 8, 1))
            .leaveType(LeaveType.FULL_DAY)
            .hours(null)
            .reason("私用")
            .status(LeaveRequestStatus.PENDING)
            .version(0L)
            .build();

        when(leaveRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        LeaveRequestResponse response = service.withdraw(pending.getId(), employee.getId());

        assertThat(response.status()).isEqualTo(LeaveRequestStatus.WITHDRAWN);
        verify(leaveGrantService, never()).restoreDays(any(), any());
    }

    @Test
    @DisplayName("APPROVED取り下げ時にusedDaysが減算される")
    void withdraw_approved_success_restoresUsedDays() {
        LeaveRequest approved = LeaveRequest.builder()
            .id(UUID.randomUUID())
            .requester(employee)
            .approver(manager)
            .targetDate(LocalDate.of(2026, 8, 1))
            .leaveType(LeaveType.HALF_DAY)
            .hours(null)
            .reason("私用")
            .status(LeaveRequestStatus.APPROVED)
            .version(0L)
            .build();

        when(leaveRequestRepository.findById(approved.getId())).thenReturn(Optional.of(approved));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        LeaveRequestResponse response = service.withdraw(approved.getId(), employee.getId());

        assertThat(response.status()).isEqualTo(LeaveRequestStatus.WITHDRAWN);
        verify(leaveGrantService).restoreDays(employee.getId(), new BigDecimal("0.5"));
    }

    @Test
    @DisplayName("上長は自己承認できる")
    void approve_selfApproval_managerSuccess() {
        LeaveRequest pending = LeaveRequest.builder()
            .id(UUID.randomUUID())
            .requester(manager)
            .targetDate(LocalDate.of(2026, 8, 1))
            .leaveType(LeaveType.FULL_DAY)
            .hours(null)
            .reason("私用")
            .status(LeaveRequestStatus.PENDING)
            .version(0L)
            .build();

        when(leaveRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(employeeRepository.findById(manager.getId())).thenReturn(Optional.of(manager));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        LeaveRequestResponse response = service.approve(pending.getId(), manager.getId());

        assertThat(response.status()).isEqualTo(LeaveRequestStatus.APPROVED);
    }
}
