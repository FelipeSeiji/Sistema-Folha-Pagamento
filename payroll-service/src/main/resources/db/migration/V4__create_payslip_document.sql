CREATE TABLE tb_payslip_document (
    payslip_id UUID PRIMARY KEY,
    pdf BYTEA NOT NULL,
    CONSTRAINT fk_payslip_document_payslip FOREIGN KEY (payslip_id) REFERENCES tb_payslip (id)
);
