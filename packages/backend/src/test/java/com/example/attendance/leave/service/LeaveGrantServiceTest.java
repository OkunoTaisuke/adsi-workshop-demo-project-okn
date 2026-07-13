package com.example.attendance.leave.service;

import com.example.attendance.employee.entity.Employee;
import com.example.attendance.employee.repository.EmployeeRepository;
import com.example.attendance.leave.domain.LeaveBalance;
import com.example.attendance.leave.domain.LeaveGrantCalculator;
import com.example.attendance.leave.entity.LeaveGrant;
import com.example.attendance.leave.repository.LeaveGrantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeaveGrantServiceTest {

    @Mock
    private LeaveGrantRepository leaveGrantRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    private LeaveGrantServiceImpl service;

    private final LocalDate TODAY = LocalDate.of(2026, 7, 13);
    private Clock fixedClock;
    private Employee employee;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(
            ZonedDateTime.of(2026, 7, 13, 9, 0, 0, 0, ZoneId.of("Asia/Tokyo")).toInstant(),
            ZoneId.of("Asia/Tokyo")
        );
        service = new LeaveGrantServiceImpl(leaveGrantRepository, employeeRepository, new LeaveGrantCalculator(), fixedClock);

        employee = Employee.builder()
            .id(UUID.randomUUID())
            .name("田中太郎")
            .hireDate(LocalDate.of(2024, 4, 1))
            .build();
    }

    @Test
    @DisplayName("初回照会時に未付与分をオンデマンド生成して残日数を返す")
    void getBalance_noGrants_generatesAndReturns() {
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(leaveGrantRepository.findByEmployeeIdAndGrantDate(any(), any()))
            .thenReturn(Optional.empty());
        when(leaveGrantRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(leaveGrantRepository.findByEmployeeIdAndExpiryDateAfterOrderByExpiryDateAsc(any(), any()))
            .thenAnswer(inv -> {
                @SuppressWarnings("unchecked")
                ArgumentCaptor<List<LeaveGrant>> captor = ArgumentCaptor.forClass(List.class);
                return List.of();
            });

        // hireDate=2024-04-01, today=2026-07-13
        // Grant dates: 2024-10-01 (0.5y=10d), 2025-10-01 (1.5y=11d)
        // After save, the valid grants query returns saved grants
        // We need to return the generated grants after saveAll
        LeaveGrant grant1 = LeaveGrant.builder()
            .id(UUID.randomUUID()).employee(employee)
            .fiscalYear(2024).grantDate(LocalDate.of(2024, 10, 1))
            .grantedDays(BigDecimal.TEN).usedDays(BigDecimal.ZERO)
            .expiryDate(LocalDate.of(2027, 3, 31)).build();
        LeaveGrant grant2 = LeaveGrant.builder()
            .id(UUID.randomUUID()).employee(employee)
            .fiscalYear(2025).grantDate(LocalDate.of(2025, 10, 1))
            .grantedDays(BigDecimal.valueOf(11)).usedDays(BigDecimal.ZERO)
            .expiryDate(LocalDate.of(2028, 3, 31)).build();

        when(leaveGrantRepository.findByEmployeeIdAndExpiryDateAfterOrderByExpiryDateAsc(employee.getId(), TODAY))
            .thenReturn(List.of(grant1, grant2));

        LeaveBalance balance = service.getBalance(employee.getId());

        // 10 + 11 = 21
        assertThat(balance.remainingDays()).isEqualByComparingTo(BigDecimal.valueOf(21));
        assertThat(balance.totalGrantedDays()).isEqualByComparingTo(BigDecimal.valueOf(21));
    }

    @Test
    @DisplayName("既存の有効な付与がある場合はそのまま残日数を返す")
    void getBalance_existingGrants_returnsBalance() {
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));

        LeaveGrant grant1 = LeaveGrant.builder()
            .id(UUID.randomUUID()).employee(employee)
            .fiscalYear(2024).grantDate(LocalDate.of(2024, 10, 1))
            .grantedDays(BigDecimal.TEN).usedDays(BigDecimal.valueOf(3))
            .expiryDate(LocalDate.of(2027, 3, 31)).build();
        LeaveGrant grant2 = LeaveGrant.builder()
            .id(UUID.randomUUID()).employee(employee)
            .fiscalYear(2025).grantDate(LocalDate.of(2025, 10, 1))
            .grantedDays(BigDecimal.valueOf(11)).usedDays(BigDecimal.ZERO)
            .expiryDate(LocalDate.of(2028, 3, 31)).build();

        when(leaveGrantRepository.findByEmployeeIdAndGrantDate(employee.getId(), LocalDate.of(2024, 10, 1)))
            .thenReturn(Optional.of(grant1));
        when(leaveGrantRepository.findByEmployeeIdAndGrantDate(employee.getId(), LocalDate.of(2025, 10, 1)))
            .thenReturn(Optional.of(grant2));
        when(leaveGrantRepository.findByEmployeeIdAndExpiryDateAfterOrderByExpiryDateAsc(employee.getId(), TODAY))
            .thenReturn(List.of(grant1, grant2));

        LeaveBalance balance = service.getBalance(employee.getId());

        assertThat(balance.usedDays()).isEqualByComparingTo(BigDecimal.valueOf(3));
        assertThat(balance.remainingDays()).isEqualByComparingTo(BigDecimal.valueOf(18));
    }

    @Test
    @DisplayName("失効した付与は残日数に含めない")
    void getBalance_expiredGrants_excludedFromRemaining() {
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));

        // Only the valid grant is returned by the expiry query
        LeaveGrant validGrant = LeaveGrant.builder()
            .id(UUID.randomUUID()).employee(employee)
            .fiscalYear(2025).grantDate(LocalDate.of(2025, 10, 1))
            .grantedDays(BigDecimal.valueOf(11)).usedDays(BigDecimal.ZERO)
            .expiryDate(LocalDate.of(2028, 3, 31)).build();

        when(leaveGrantRepository.findByEmployeeIdAndGrantDate(employee.getId(), LocalDate.of(2024, 10, 1)))
            .thenReturn(Optional.of(LeaveGrant.builder().id(UUID.randomUUID()).build()));
        when(leaveGrantRepository.findByEmployeeIdAndGrantDate(employee.getId(), LocalDate.of(2025, 10, 1)))
            .thenReturn(Optional.of(validGrant));
        when(leaveGrantRepository.findByEmployeeIdAndExpiryDateAfterOrderByExpiryDateAsc(employee.getId(), TODAY))
            .thenReturn(List.of(validGrant));

        LeaveBalance balance = service.getBalance(employee.getId());

        assertThat(balance.totalGrantedDays()).isEqualByComparingTo(BigDecimal.valueOf(11));
        assertThat(balance.remainingDays()).isEqualByComparingTo(BigDecimal.valueOf(11));
    }

    @Test
    @DisplayName("繰越分が正しく識別される（前年度の有効付与 = 繰越）")
    void getBalance_carriedOver_identifiedCorrectly() {
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));

        // fiscalYear=2024 (前々年度) — still valid (expiry 2027-03-31 > today 2026-07-13)
        LeaveGrant carriedGrant = LeaveGrant.builder()
            .id(UUID.randomUUID()).employee(employee)
            .fiscalYear(2024).grantDate(LocalDate.of(2024, 10, 1))
            .grantedDays(BigDecimal.TEN).usedDays(BigDecimal.valueOf(4))
            .expiryDate(LocalDate.of(2027, 3, 31)).build();

        // fiscalYear=2025 — still valid
        LeaveGrant lastYearGrant = LeaveGrant.builder()
            .id(UUID.randomUUID()).employee(employee)
            .fiscalYear(2025).grantDate(LocalDate.of(2025, 10, 1))
            .grantedDays(BigDecimal.valueOf(11)).usedDays(BigDecimal.valueOf(2))
            .expiryDate(LocalDate.of(2028, 3, 31)).build();

        when(leaveGrantRepository.findByEmployeeIdAndGrantDate(employee.getId(), LocalDate.of(2024, 10, 1)))
            .thenReturn(Optional.of(carriedGrant));
        when(leaveGrantRepository.findByEmployeeIdAndGrantDate(employee.getId(), LocalDate.of(2025, 10, 1)))
            .thenReturn(Optional.of(lastYearGrant));
        when(leaveGrantRepository.findByEmployeeIdAndExpiryDateAfterOrderByExpiryDateAsc(employee.getId(), TODAY))
            .thenReturn(List.of(carriedGrant, lastYearGrant));

        LeaveBalance balance = service.getBalance(employee.getId());

        // current fiscal year = 2026, both grants are from earlier → carried over
        assertThat(balance.carriedOverDays()).isEqualByComparingTo(BigDecimal.valueOf(15));
        assertThat(balance.currentYearGrantDays()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(balance.grants()).hasSize(2);
        assertThat(balance.grants().get(0).isCarriedOver()).isTrue();
        assertThat(balance.grants().get(1).isCarriedOver()).isTrue();
    }

    @Test
    @DisplayName("既に付与済みの基準日は二重生成しない")
    void generateGrants_doesNotDuplicate() {
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));

        LeaveGrant existingGrant = LeaveGrant.builder()
            .id(UUID.randomUUID()).employee(employee)
            .fiscalYear(2024).grantDate(LocalDate.of(2024, 10, 1))
            .grantedDays(BigDecimal.TEN).usedDays(BigDecimal.ZERO)
            .expiryDate(LocalDate.of(2027, 3, 31)).build();

        // First grant date exists, second does not
        when(leaveGrantRepository.findByEmployeeIdAndGrantDate(employee.getId(), LocalDate.of(2024, 10, 1)))
            .thenReturn(Optional.of(existingGrant));
        when(leaveGrantRepository.findByEmployeeIdAndGrantDate(employee.getId(), LocalDate.of(2025, 10, 1)))
            .thenReturn(Optional.empty());
        when(leaveGrantRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(leaveGrantRepository.findByEmployeeIdAndExpiryDateAfterOrderByExpiryDateAsc(employee.getId(), TODAY))
            .thenReturn(List.of(existingGrant));

        service.getBalance(employee.getId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LeaveGrant>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(leaveGrantRepository).saveAll(captor.capture());
        // Only 1 new grant created (2025-10-01), not the existing one
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getGrantDate()).isEqualTo(LocalDate.of(2025, 10, 1));
    }
}
