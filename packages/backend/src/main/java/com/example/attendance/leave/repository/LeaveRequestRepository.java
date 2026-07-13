package com.example.attendance.leave.repository;

import com.example.attendance.leave.domain.LeaveRequestStatus;
import com.example.attendance.leave.domain.LeaveType;
import com.example.attendance.leave.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {

    List<LeaveRequest> findByRequesterIdOrderByCreatedAtDesc(UUID requesterId);

    List<LeaveRequest> findByRequesterIdAndStatusOrderByCreatedAtDesc(UUID requesterId, LeaveRequestStatus status);

    List<LeaveRequest> findByRequesterIdAndTargetDateAndStatusIn(
        UUID requesterId, LocalDate targetDate, List<LeaveRequestStatus> statuses);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.requester.department.id = :departmentId AND lr.status = 'PENDING' ORDER BY lr.createdAt ASC")
    List<LeaveRequest> findPendingByDepartmentId(@Param("departmentId") UUID departmentId);

    @Query("SELECT COALESCE(SUM(lr.hours), 0) FROM LeaveRequest lr WHERE lr.requester.id = :requesterId AND lr.leaveType = :leaveType AND lr.status IN :statuses AND lr.targetDate >= :fiscalYearStart AND lr.targetDate <= :fiscalYearEnd")
    int sumHoursByRequesterAndTypeAndStatusInRange(
        @Param("requesterId") UUID requesterId,
        @Param("leaveType") LeaveType leaveType,
        @Param("statuses") List<LeaveRequestStatus> statuses,
        @Param("fiscalYearStart") LocalDate fiscalYearStart,
        @Param("fiscalYearEnd") LocalDate fiscalYearEnd);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.requester.id IN :requesterIds AND lr.status = :status AND lr.targetDate BETWEEN :start AND :end")
    List<LeaveRequest> findByRequesterIdInAndStatusAndTargetDateBetween(
        @Param("requesterIds") List<UUID> requesterIds,
        @Param("status") LeaveRequestStatus status,
        @Param("start") LocalDate start,
        @Param("end") LocalDate end);

    @Query("SELECT lr FROM LeaveRequest lr JOIN FETCH lr.approver WHERE lr.requester.id = :requesterId ORDER BY lr.createdAt DESC")
    List<LeaveRequest> findByRequesterIdWithApprover(@Param("requesterId") UUID requesterId);

    @Query("SELECT lr FROM LeaveRequest lr JOIN FETCH lr.approver WHERE lr.requester.id = :requesterId AND lr.status = :status ORDER BY lr.createdAt DESC")
    List<LeaveRequest> findByRequesterIdAndStatusWithApprover(@Param("requesterId") UUID requesterId, @Param("status") LeaveRequestStatus status);
}
