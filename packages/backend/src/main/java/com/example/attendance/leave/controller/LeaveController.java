package com.example.attendance.leave.controller;

import com.example.attendance.leave.domain.LeaveBalance;
import com.example.attendance.leave.dto.LeaveBalanceResponse;
import com.example.attendance.leave.dto.LeavePendingResponse;
import com.example.attendance.leave.dto.LeaveRejectRequest;
import com.example.attendance.leave.dto.LeaveRequestCreateRequest;
import com.example.attendance.leave.dto.LeaveRequestResponse;
import com.example.attendance.leave.dto.LeaveSummaryResponse;
import com.example.attendance.leave.service.LeaveGrantService;
import com.example.attendance.leave.service.LeaveService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/leaves")
public class LeaveController {

    private final LeaveGrantService leaveGrantService;
    private final LeaveService leaveService;

    public LeaveController(LeaveGrantService leaveGrantService, LeaveService leaveService) {
        this.leaveGrantService = leaveGrantService;
        this.leaveService = leaveService;
    }

    @GetMapping("/balance")
    public ResponseEntity<LeaveBalanceResponse> getBalance(@RequestParam UUID employeeId) {
        LeaveBalance balance = leaveGrantService.getBalance(employeeId);
        return ResponseEntity.ok(LeaveBalanceResponse.from(balance));
    }

    @PostMapping("/requests")
    public ResponseEntity<LeaveRequestResponse> createRequest(
            @RequestParam UUID employeeId,
            @Valid @RequestBody LeaveRequestCreateRequest request) {
        LeaveRequestResponse response = leaveService.createRequest(employeeId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/requests")
    public ResponseEntity<List<LeaveRequestResponse>> getMyRequests(
            @RequestParam UUID employeeId,
            @RequestParam(required = false) String status) {
        List<LeaveRequestResponse> responses = leaveService.getMyRequests(employeeId, status);
        return ResponseEntity.ok(responses);
    }

    @PatchMapping("/requests/{id}/withdraw")
    public ResponseEntity<LeaveRequestResponse> withdraw(
            @PathVariable UUID id,
            @RequestParam UUID employeeId) {
        LeaveRequestResponse response = leaveService.withdraw(id, employeeId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/requests/pending")
    public ResponseEntity<List<LeavePendingResponse>> getPendingRequests(@RequestParam UUID approverId) {
        List<LeavePendingResponse> responses = leaveService.getPendingRequests(approverId);
        return ResponseEntity.ok(responses);
    }

    @PatchMapping("/requests/{id}/approve")
    public ResponseEntity<LeaveRequestResponse> approve(
            @PathVariable UUID id,
            @RequestParam UUID approverId) {
        LeaveRequestResponse response = leaveService.approve(id, approverId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/requests/{id}/reject")
    public ResponseEntity<LeaveRequestResponse> reject(
            @PathVariable UUID id,
            @RequestParam UUID approverId,
            @Valid @RequestBody LeaveRejectRequest request) {
        LeaveRequestResponse response = leaveService.reject(id, approverId, request.reason());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/summary")
    public ResponseEntity<LeaveSummaryResponse> getSummary(
            @RequestParam(required = false) Integer fiscalYear,
            @RequestParam(required = false) UUID departmentId) {
        LeaveSummaryResponse response = leaveService.getSummary(fiscalYear, departmentId);
        return ResponseEntity.ok(response);
    }
}
