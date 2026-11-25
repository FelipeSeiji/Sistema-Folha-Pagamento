CREATE TABLE tb_payroll_outbox (
    id UUID PRIMARY KEY,
    payroll_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT uk_payroll_outbox_payroll_event UNIQUE (payroll_id, event_type)
);

CREATE INDEX ix_payroll_outbox_pending ON tb_payroll_outbox (published_at, occurred_at, id);
