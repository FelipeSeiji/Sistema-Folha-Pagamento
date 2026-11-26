ALTER TABLE tb_attendance_summary ADD COLUMN company_id UUID;

CREATE INDEX ix_attendance_summary_company ON tb_attendance_summary (company_id);
