package com.example.attendance.leave.service;

import com.example.attendance.employee.entity.Employee;
import com.example.attendance.employee.repository.EmployeeRepository;
import com.example.attendance.leave.domain.LeaveBalance;
import com.example.attendance.leave.domain.LeaveGrantCalculator;
import com.example.attendance.leave.domain.LeaveRequestStatus;
import com.example.attendance.leave.domain.LeaveType;
import com.example.attendance.leave.dto.LeaveRequestCreateRequest;
import com.example.attendance.leave.dto.LeaveRequestResponse;
import com.example.attendance.leave.dto.LeavePendingResponse;
import com.example.attendance.leave.dto.LeaveSummaryResponse;
import com.example.attendance.leave.entity.LeaveRequest;
import com.example.attendance.leave.repository.LeaveRequestRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class LeaveServiceImpl implements LeaveService {

    private static final int STANDARD_WORK_HOURS_PER_DAY = 8;
    private static final int HOURLY_ANNUAL_LIMIT = 40;

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveGrantService leaveGrantService;
    private final EmployeeRepository employeeRepository;
    private final LeaveGrantCalculator calculator;
    private final Clock clock;

    public LeaveServiceImpl(LeaveRequestRepository leaveRequestRepository,
                            LeaveGrantService leaveGrantService,
                            EmployeeRepository employeeRepository,
                            LeaveGrantCalculator calculator,
                            Clock clock) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveGrantService = leaveGrantService;
        this.employeeRepository = employeeRepository;
        this.calculator = calculator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public LeaveRequestResponse createRequest(UUID requesterId, LeaveRequestCreateRequest request) {
        Employee requester = employeeRepository.findById(requesterId)
            .orElseThrow(() -> new EntityNotFoundException("社員が見つかりません"));

        validateNoDuplicate(requesterId, request.targetDate());
        BigDecimal consumptionDays = calculateConsumptionDays(request.leaveType(), request.hours());
        validateBalance(requesterId, consumptionDays);

        if (request.leaveType() == LeaveType.HOURLY) {
            validateHourlyLimit(requesterId, request.hours());
        }

        LeaveRequest entity = LeaveRequest.builder()
            .id(UUID.randomUUID())
            .requester(requester)
            .targetDate(request.targetDate())
            .leaveType(request.leaveType())
            .hours(request.hours())
            .reason(request.reason())
            .status(LeaveRequestStatus.PENDING)
            .build();

        LeaveRequest saved = leaveRequestRepository.save(entity);
        return LeaveRequestResponse.from(saved);
    }

    @Override
    public List<LeaveRequestResponse> getMyRequests(UUID requesterId, String status) {
        if (status != null && !status.isEmpty()) {
            LeaveRequestStatus s = LeaveRequestStatus.valueOf(status);
            return leaveRequestRepository.findByRequesterIdAndStatusWithApprover(requesterId, s)
                .stream().map(LeaveRequestResponse::from).toList();
        }
        return leaveRequestRepository.findByRequesterIdWithApprover(requesterId)
            .stream().map(LeaveRequestResponse::from).toList();
    }

    @Override
    public List<LeavePendingResponse> getPendingRequests(UUID approverId) {
        Employee approver = employeeRepository.findById(approverId)
            .orElseThrow(() -> new EntityNotFoundException("社員が見つかりません"));
        return leaveRequestRepository.findPendingByDepartmentId(approver.getDepartment().getId())
            .stream().map(LeavePendingResponse::from).toList();
    }

    @Override
    @Transactional
    public LeaveRequestResponse approve(UUID requestId, UUID approverId) {
        LeaveRequest request = leaveRequestRepository.findById(requestId)
            .orElseThrow(() -> new EntityNotFoundException("申請が見つかりません"));
        Employee approver = employeeRepository.findById(approverId)
            .orElseThrow(() -> new EntityNotFoundException("承認者が見つかりません"));

        validateApprovalAuthority(approver, request.getRequester());

        request.setStatus(LeaveRequestStatus.APPROVED);
        request.setApprover(approver);

        BigDecimal days = calculateConsumptionDays(request.getLeaveType(), request.getHours());
        leaveGrantService.consumeDays(request.getRequester().getId(), days);

        LeaveRequest saved = leaveRequestRepository.save(request);
        return LeaveRequestResponse.from(saved);
    }

    @Override
    @Transactional
    public LeaveRequestResponse reject(UUID requestId, UUID approverId, String reason) {
        LeaveRequest request = leaveRequestRepository.findById(requestId)
            .orElseThrow(() -> new EntityNotFoundException("申請が見つかりません"));
        Employee approver = employeeRepository.findById(approverId)
            .orElseThrow(() -> new EntityNotFoundException("承認者が見つかりません"));

        validateApprovalAuthority(approver, request.getRequester());

        request.setStatus(LeaveRequestStatus.REJECTED);
        request.setApprover(approver);
        request.setRejectReason(reason);

        LeaveRequest saved = leaveRequestRepository.save(request);
        return LeaveRequestResponse.from(saved);
    }

    @Override
    @Transactional
    public LeaveRequestResponse withdraw(UUID requestId, UUID requesterId) {
        LeaveRequest request = leaveRequestRepository.findById(requestId)
            .orElseThrow(() -> new EntityNotFoundException("申請が見つかりません"));

        if (!request.getRequester().getId().equals(requesterId)) {
            throw new IllegalStateException("自分の申請のみ取り下げ可能です");
        }
        if (request.getStatus() == LeaveRequestStatus.WITHDRAWN || request.getStatus() == LeaveRequestStatus.REJECTED) {
            throw new IllegalStateException("既に取り下げ済みまたは却下済みです");
        }

        boolean wasApproved = request.getStatus() == LeaveRequestStatus.APPROVED;
        request.setStatus(LeaveRequestStatus.WITHDRAWN);

        if (wasApproved) {
            BigDecimal days = calculateConsumptionDays(request.getLeaveType(), request.getHours());
            leaveGrantService.restoreDays(request.getRequester().getId(), days);
        }

        LeaveRequest saved = leaveRequestRepository.save(request);
        return LeaveRequestResponse.from(saved);
    }

    @Override
    public LeaveSummaryResponse getSummary(Integer fiscalYear, UUID departmentId) {
        LocalDate today = LocalDate.now(clock);
        int year = fiscalYear != null ? fiscalYear : calculator.determineFiscalYear(today);

        List<Employee> employees;
        if (departmentId != null) {
            employees = employeeRepository.findByDepartmentId(departmentId);
        } else {
            employees = employeeRepository.findAll();
        }

        List<LeaveSummaryResponse.EmployeeLeaveSummary> summaries = employees.stream()
            .map(emp -> {
                try {
                    LeaveBalance balance = leaveGrantService.getBalance(emp.getId());
                    return new LeaveSummaryResponse.EmployeeLeaveSummary(
                        emp.getId(),
                        emp.getName(),
                        emp.getDepartment().getName(),
                        balance.totalGrantedDays(),
                        balance.usedDays(),
                        balance.remainingDays(),
                        balance.hourlyUsedHours()
                    );
                } catch (Exception e) {
                    return new LeaveSummaryResponse.EmployeeLeaveSummary(
                        emp.getId(), emp.getName(), emp.getDepartment().getName(),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0
                    );
                }
            })
            .toList();

        return new LeaveSummaryResponse(year, summaries);
    }

    private BigDecimal calculateConsumptionDays(LeaveType leaveType, Integer hours) {
        return switch (leaveType) {
            case FULL_DAY -> BigDecimal.ONE;
            case HALF_DAY -> new BigDecimal("0.5");
            case HOURLY -> new BigDecimal(hours).divide(
                BigDecimal.valueOf(STANDARD_WORK_HOURS_PER_DAY), 2, java.math.RoundingMode.HALF_UP);
        };
    }

    private void validateNoDuplicate(UUID requesterId, LocalDate targetDate) {
        List<LeaveRequestStatus> activeStatuses = List.of(LeaveRequestStatus.PENDING, LeaveRequestStatus.APPROVED);
        List<LeaveRequest> existing = leaveRequestRepository
            .findByRequesterIdAndTargetDateAndStatusIn(requesterId, targetDate, activeStatuses);
        if (!existing.isEmpty()) {
            throw new IllegalStateException("同日に既に申請があります");
        }
    }

    private void validateBalance(UUID requesterId, BigDecimal requiredDays) {
        LeaveBalance balance = leaveGrantService.getBalance(requesterId);
        if (balance.remainingDays().compareTo(requiredDays) < 0) {
            throw new IllegalStateException("残日数が不足しています");
        }
    }

    private void validateHourlyLimit(UUID requesterId, int hours) {
        LocalDate today = LocalDate.now(clock);
        int fiscalYear = calculator.determineFiscalYear(today);
        LocalDate fiscalStart = LocalDate.of(fiscalYear, 4, 1);
        LocalDate fiscalEnd = LocalDate.of(fiscalYear + 1, 3, 31);

        List<LeaveRequestStatus> countStatuses = List.of(LeaveRequestStatus.PENDING, LeaveRequestStatus.APPROVED);
        int usedHours = leaveRequestRepository.sumHoursByRequesterAndTypeAndStatusInRange(
            requesterId, LeaveType.HOURLY, countStatuses, fiscalStart, fiscalEnd);

        if (usedHours + hours > HOURLY_ANNUAL_LIMIT) {
            throw new IllegalStateException("時間単位年休の年間上限（40時間）を超過します");
        }
    }

    private void validateApprovalAuthority(Employee approver, Employee requester) {
        // Self-approval for managers
        if (approver.getId().equals(requester.getId()) && approver.isManager()) {
            return;
        }
        // Manager of same department
        if (approver.isManager() &&
            approver.getDepartment().getId().equals(requester.getDepartment().getId())) {
            return;
        }
        throw new IllegalStateException("承認権限がありません");
    }
}
