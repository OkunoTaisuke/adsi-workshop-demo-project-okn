CREATE TABLE leave_grants (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES employees(id),
    fiscal_year INTEGER NOT NULL,
    grant_date DATE NOT NULL,
    granted_days DECIMAL(4,1) NOT NULL,
    used_days DECIMAL(4,1) NOT NULL DEFAULT 0,
    expiry_date DATE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_leave_grants_employee ON leave_grants(employee_id);
CREATE INDEX idx_leave_grants_employee_fiscal ON leave_grants(employee_id, fiscal_year);
CREATE UNIQUE INDEX idx_leave_grants_employee_grant_date ON leave_grants(employee_id, grant_date);
