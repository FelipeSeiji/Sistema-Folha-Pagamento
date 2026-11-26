CREATE TABLE tb_closed_payroll_summary (
    payroll_id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    company_id UUID NOT NULL,
    period DATE NOT NULL,
    closed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    payslip_count BIGINT NOT NULL,
    total_earnings NUMERIC(38, 2) NOT NULL,
    total_deductions NUMERIC(38, 2) NOT NULL,
    net_pay NUMERIC(38, 2) NOT NULL,
    CONSTRAINT uk_closed_payroll_summary_event UNIQUE (event_id),
    CONSTRAINT fk_closed_payroll_summary_payroll FOREIGN KEY (payroll_id) REFERENCES tb_payroll (id)
);
