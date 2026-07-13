package com.example.attendance.attendance.service;

import com.example.attendance.attendance.dto.AttendanceHistoryResponse;
import com.example.attendance.attendance.dto.AttendanceRecordResponse;
import com.example.attendance.attendance.dto.TeamMemberSummaryResponse;
import com.example.attendance.attendance.dto.TodayStatusResponse;

import java.util.List;
import java.util.UUID;

public interface AttendanceService {

    AttendanceRecordResponse clockIn(UUID employeeId);

    AttendanceRecordResponse clockIn(UUID employeeId, String memo);

    AttendanceRecordResponse clockOut(UUID employeeId);

    AttendanceRecordResponse clockOut(UUID employeeId, String memo);

    AttendanceRecordResponse updateMemo(UUID recordId, UUID employeeId, String memo);

    TodayStatusResponse getTodayStatus(UUID employeeId);

    AttendanceHistoryResponse getHistory(UUID employeeId, String month);

    List<TeamMemberSummaryResponse> getTeamAttendance(UUID managerId, String month);

    List<TeamMemberSummaryResponse> getAllAttendance(String month, UUID departmentId);
}
