CREATE TABLE leave_requests (
    id UUID PRIMARY KEY,
    requester_id UUID NOT NULL REFERENCES employees(id),
    approver_id UUID REFERENCES employees(id),
    target_date DATE NOT NULL,
    leave_type VARCHAR(20) NOT NULL CHECK (leave_type IN ('FULL_DAY', 'HALF_DAY', 'HOURLY')),
    hours INTEGER,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    reject_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_hours_for_hourly CHECK (
        (leave_type = 'HOURLY' AND hours BETWEEN 1 AND 7) OR
        (leave_type != 'HOURLY' AND hours IS NULL)
    )
);

CREATE INDEX idx_leave_requests_requester ON leave_requests(requester_id);
CREATE INDEX idx_leave_requests_status ON leave_requests(status);
CREATE INDEX idx_leave_requests_target_date ON leave_requests(target_date);
