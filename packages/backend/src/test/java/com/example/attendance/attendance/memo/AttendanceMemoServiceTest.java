package com.example.attendance.attendance.memo;

import com.example.attendance.attendance.entity.AttendanceRecord;
import com.example.attendance.attendance.repository.AttendanceRecordRepository;
import com.example.attendance.attendance.service.AttendanceServiceImpl;
import com.example.attendance.department.entity.Department;
import com.example.attendance.employee.entity.Employee;
import com.example.attendance.employee.entity.Role;
import com.example.attendance.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceMemoServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2025-01-15T00:00:00Z");
    private static final ZoneId ZONE_TOKYO = ZoneId.of("Asia/Tokyo");
    private static final LocalDate TODAY_TOKYO = LocalDate.of(2025, 1, 15);

    @Mock
    private AttendanceRecordRepository attendanceRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    private AttendanceServiceImpl service;

    private Employee employee;
    private UUID employeeId;

    @BeforeEach
    void setUp() {
        var clock = Clock.fixed(FIXED_INSTANT, ZONE_TOKYO);
        service = new AttendanceServiceImpl(attendanceRepository, employeeRepository, clock);

        var department = Department.builder()
                .id(UUID.randomUUID())
                .name("Engineering")
                .build();

        employeeId = UUID.randomUUID();
        employee = Employee.builder()
                .id(employeeId)
                .name("田中太郎")
                .email("tanaka@example.com")
                .password("hashed")
                .department(department)
                .role(Role.EMPLOYEE)
                .isManager(false)
                .hireDate(LocalDate.of(2024, 4, 1))
                .build();
    }

    @Nested
    @DisplayName("出勤打刻時のメモ入力")
    class ClockInWithMemo {

        @Test
        @DisplayName("出勤打刻時にメモを付与できる")
        void clockIn_withMemo_savesMemo() {
            // Arrange
            when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
            when(attendanceRepository.findByEmployeeIdAndWorkDateAndClockOutIsNull(employeeId, TODAY_TOKYO))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.save(any(AttendanceRecord.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            var result = service.clockIn(employeeId, "直行のため");

            // Assert
            assertThat(result.memo()).isEqualTo("直行のため");
        }

        @Test
        @DisplayName("出勤打刻時にメモなし（null）でも打刻できる")
        void clockIn_withoutMemo_succeeds() {
            // Arrange
            when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
            when(attendanceRepository.findByEmployeeIdAndWorkDateAndClockOutIsNull(employeeId, TODAY_TOKYO))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.save(any(AttendanceRecord.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            var result = service.clockIn(employeeId, null);

            // Assert
            assertThat(result.memo()).isNull();
            assertThat(result.clockIn()).isNotNull();
        }

        @Test
        @DisplayName("出勤打刻時にメモ空文字でも打刻できる")
        void clockIn_withEmptyMemo_succeeds() {
            // Arrange
            when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
            when(attendanceRepository.findByEmployeeIdAndWorkDateAndClockOutIsNull(employeeId, TODAY_TOKYO))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.save(any(AttendanceRecord.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            var result = service.clockIn(employeeId, "");

            // Assert
            assertThat(result.memo()).isEmpty();
            assertThat(result.clockIn()).isNotNull();
        }
    }

    @Nested
    @DisplayName("退勤打刻時のメモ入力")
    class ClockOutWithMemo {

        @Test
        @DisplayName("退勤打刻時にメモを付与できる")
        void clockOut_withMemo_savesMemo() {
            // Arrange
            var openRecord = AttendanceRecord.builder()
                    .id(UUID.randomUUID())
                    .employee(employee)
                    .workDate(TODAY_TOKYO)
                    .clockIn(Instant.parse("2025-01-14T23:00:00Z"))
                    .build();
            when(attendanceRepository.findByEmployeeIdAndWorkDateAndClockOutIsNull(employeeId, TODAY_TOKYO))
                    .thenReturn(Optional.of(openRecord));
            when(attendanceRepository.save(any(AttendanceRecord.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            var result = service.clockOut(employeeId, "早退：体調不良");

            // Assert
            assertThat(result.memo()).isEqualTo("早退：体調不良");
        }

        @Test
        @DisplayName("退勤打刻時にメモなしでも退勤できる")
        void clockOut_withoutMemo_succeeds() {
            // Arrange
            var openRecord = AttendanceRecord.builder()
                    .id(UUID.randomUUID())
                    .employee(employee)
                    .workDate(TODAY_TOKYO)
                    .clockIn(Instant.parse("2025-01-14T23:00:00Z"))
                    .build();
            when(attendanceRepository.findByEmployeeIdAndWorkDateAndClockOutIsNull(employeeId, TODAY_TOKYO))
                    .thenReturn(Optional.of(openRecord));
            when(attendanceRepository.save(any(AttendanceRecord.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            var result = service.clockOut(employeeId, null);

            // Assert
            assertThat(result.memo()).isNull();
            assertThat(result.clockOut()).isNotNull();
        }
    }

    @Nested
    @DisplayName("メモのバリデーション")
    class MemoValidation {

        @Test
        @DisplayName("200文字のメモは保存できる")
        void clockIn_with200CharMemo_succeeds() {
            // Arrange
            var memo200 = "あ".repeat(200);
            when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
            when(attendanceRepository.findByEmployeeIdAndWorkDateAndClockOutIsNull(employeeId, TODAY_TOKYO))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.save(any(AttendanceRecord.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            var result = service.clockIn(employeeId, memo200);

            // Assert
            assertThat(result.memo()).hasSize(200);
        }

        @Test
        @DisplayName("201文字のメモはバリデーションエラーになる")
        void clockIn_with201CharMemo_throwsValidationError() {
            // Arrange
            var memo201 = "あ".repeat(201);
            when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
            when(attendanceRepository.findByEmployeeIdAndWorkDateAndClockOutIsNull(employeeId, TODAY_TOKYO))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.clockIn(employeeId, memo201))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("200");
        }
    }

    @Nested
    @DisplayName("メモの後追い更新")
    class UpdateMemo {

        @Test
        @DisplayName("打刻後にメモを追加できる")
        void updateMemo_addsNewMemo() {
            // Arrange
            var recordId = UUID.randomUUID();
            var record = AttendanceRecord.builder()
                    .id(recordId)
                    .employee(employee)
                    .workDate(TODAY_TOKYO)
                    .clockIn(Instant.parse("2025-01-14T23:00:00Z"))
                    .clockOut(Instant.parse("2025-01-15T08:00:00Z"))
                    .build();
            when(attendanceRepository.findById(recordId)).thenReturn(Optional.of(record));
            when(attendanceRepository.save(any(AttendanceRecord.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            var result = service.updateMemo(recordId, employeeId, "在宅勤務");

            // Assert
            assertThat(result.memo()).isEqualTo("在宅勤務");
        }

        @Test
        @DisplayName("既存メモを上書き更新できる")
        void updateMemo_overwritesExistingMemo() {
            // Arrange
            var recordId = UUID.randomUUID();
            var record = AttendanceRecord.builder()
                    .id(recordId)
                    .employee(employee)
                    .workDate(TODAY_TOKYO)
                    .clockIn(Instant.parse("2025-01-14T23:00:00Z"))
                    .memo("直行")
                    .build();
            when(attendanceRepository.findById(recordId)).thenReturn(Optional.of(record));
            when(attendanceRepository.save(any(AttendanceRecord.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            var result = service.updateMemo(recordId, employeeId, "直行直帰");

            // Assert
            assertThat(result.memo()).isEqualTo("直行直帰");
        }

        @Test
        @DisplayName("メモを削除（null に更新）できる")
        void updateMemo_deleteMemo() {
            // Arrange
            var recordId = UUID.randomUUID();
            var record = AttendanceRecord.builder()
                    .id(recordId)
                    .employee(employee)
                    .workDate(TODAY_TOKYO)
                    .clockIn(Instant.parse("2025-01-14T23:00:00Z"))
                    .memo("不要なメモ")
                    .build();
            when(attendanceRepository.findById(recordId)).thenReturn(Optional.of(record));
            when(attendanceRepository.save(any(AttendanceRecord.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            var result = service.updateMemo(recordId, employeeId, null);

            // Assert
            assertThat(result.memo()).isNull();
        }

        @Test
        @DisplayName("本人以外はメモを更新できない")
        void updateMemo_byOtherEmployee_throwsForbidden() {
            // Arrange
            var recordId = UUID.randomUUID();
            var otherEmployeeId = UUID.randomUUID();
            var record = AttendanceRecord.builder()
                    .id(recordId)
                    .employee(employee)
                    .workDate(TODAY_TOKYO)
                    .clockIn(Instant.parse("2025-01-14T23:00:00Z"))
                    .build();
            when(attendanceRepository.findById(recordId)).thenReturn(Optional.of(record));

            // Act & Assert
            assertThatThrownBy(() -> service.updateMemo(recordId, otherEmployeeId, "不正なメモ"))
                    .hasMessageContaining("forbidden");
        }
    }

    @Nested
    @DisplayName("メモの月締め制約")
    class MemoDeadline {

        @Test
        @DisplayName("月末締め後のレコードのメモは更新できない")
        void updateMemo_afterMonthEnd_throwsError() {
            // Arrange: 12月のレコードを1月に更新しようとする
            var recordId = UUID.randomUUID();
            var decemberDate = LocalDate.of(2024, 12, 15);
            var record = AttendanceRecord.builder()
                    .id(recordId)
                    .employee(employee)
                    .workDate(decemberDate)
                    .clockIn(Instant.parse("2024-12-14T23:00:00Z"))
                    .clockOut(Instant.parse("2024-12-15T08:00:00Z"))
                    .memo("出張")
                    .build();
            when(attendanceRepository.findById(recordId)).thenReturn(Optional.of(record));

            // Act & Assert（現在は2025-01-15なので、12月分は締め後）
            assertThatThrownBy(() -> service.updateMemo(recordId, employeeId, "修正メモ"))
                    .hasMessageContaining("締め");
        }

        @Test
        @DisplayName("当月のレコードのメモは更新できる")
        void updateMemo_withinCurrentMonth_succeeds() {
            // Arrange
            var recordId = UUID.randomUUID();
            var record = AttendanceRecord.builder()
                    .id(recordId)
                    .employee(employee)
                    .workDate(TODAY_TOKYO)
                    .clockIn(Instant.parse("2025-01-14T23:00:00Z"))
                    .clockOut(Instant.parse("2025-01-15T08:00:00Z"))
                    .build();
            when(attendanceRepository.findById(recordId)).thenReturn(Optional.of(record));
            when(attendanceRepository.save(any(AttendanceRecord.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            var result = service.updateMemo(recordId, employeeId, "追加メモ");

            // Assert
            assertThat(result.memo()).isEqualTo("追加メモ");
        }
    }

    @Nested
    @DisplayName("勤怠履歴でのメモ表示")
    class MemoInHistory {

        @Test
        @DisplayName("履歴レスポンスにメモが含まれる")
        void getHistory_includesMemoInRecords() {
            // Arrange
            var record = AttendanceRecord.builder()
                    .id(UUID.randomUUID())
                    .employee(employee)
                    .workDate(TODAY_TOKYO)
                    .clockIn(Instant.parse("2025-01-14T23:00:00Z"))
                    .clockOut(Instant.parse("2025-01-15T08:00:00Z"))
                    .memo("直行直帰")
                    .corrected(false)
                    .build();
            when(attendanceRepository.findByEmployeeIdAndWorkDateBetween(
                    employeeId, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31)))
                    .thenReturn(java.util.List.of(record));

            // Act
            var result = service.getHistory(employeeId, "2025-01");

            // Assert
            assertThat(result.days()).hasSize(1);
            assertThat(result.days().get(0).records().get(0).memo()).isEqualTo("直行直帰");
        }
    }
}
