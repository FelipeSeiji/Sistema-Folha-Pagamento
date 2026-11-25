CREATE TABLE tb_payroll (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL,
    company_id UUID NOT NULL,
    period DATE NOT NULL,
    type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    snapshot_captured_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    snapshot_payload JSONB NOT NULL,
    CONSTRAINT uk_payroll_company_period_type UNIQUE (company_id, period, type),
    CONSTRAINT ck_payroll_period_first_day CHECK (EXTRACT(DAY FROM period) = 1)
);

CREATE TABLE tb_rubric (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    description VARCHAR(255) NOT NULL,
    type VARCHAR(30) NOT NULL,
    subject_to_inss BOOLEAN NOT NULL,
    subject_to_irrf BOOLEAN NOT NULL,
    subject_to_fgts BOOLEAN NOT NULL,
    CONSTRAINT uk_rubric_company_code UNIQUE (company_id, code)
);

CREATE TABLE tb_attendance_summary (
    id UUID PRIMARY KEY,
    contract_id UUID NOT NULL,
    period DATE NOT NULL,
    worked_minutes BIGINT NOT NULL,
    overtime_minutes BIGINT NOT NULL,
    absence_minutes BIGINT NOT NULL,
    CONSTRAINT uk_attendance_summary_contract_period UNIQUE (contract_id, period),
    CONSTRAINT ck_attendance_summary_period_first_day CHECK (EXTRACT(DAY FROM period) = 1)
);

CREATE TABLE tb_payslip (
    id UUID PRIMARY KEY,
    payroll_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    applied_base_salary NUMERIC(19, 2) NOT NULL,
    total_earnings NUMERIC(19, 2) NOT NULL,
    total_deductions NUMERIC(19, 2) NOT NULL,
    net_pay NUMERIC(19, 2) NOT NULL,
    CONSTRAINT fk_payslip_payroll FOREIGN KEY (payroll_id) REFERENCES tb_payroll (id),
    CONSTRAINT uk_payslip_payroll_contract UNIQUE (payroll_id, contract_id)
);

CREATE TABLE tb_payslip_item (
    payslip_id UUID NOT NULL,
    item_order INTEGER NOT NULL,
    rubric_id UUID NOT NULL,
    applied_code VARCHAR(50) NOT NULL,
    applied_description VARCHAR(255) NOT NULL,
    applied_type VARCHAR(30) NOT NULL,
    reference NUMERIC(19, 4),
    amount NUMERIC(19, 2) NOT NULL,
    PRIMARY KEY (payslip_id, item_order),
    CONSTRAINT fk_payslip_item_payslip FOREIGN KEY (payslip_id) REFERENCES tb_payslip (id),
    CONSTRAINT fk_payslip_item_rubric FOREIGN KEY (rubric_id) REFERENCES tb_rubric (id)
);

CREATE INDEX ix_payslip_item_rubric ON tb_payslip_item (rubric_id);
