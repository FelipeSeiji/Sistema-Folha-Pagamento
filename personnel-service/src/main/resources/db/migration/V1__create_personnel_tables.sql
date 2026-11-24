CREATE TABLE tb_company (
    id UUID PRIMARY KEY,
    cnpj VARCHAR(255),
    legal_name VARCHAR(255)
);

CREATE TABLE tb_department (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    company_id UUID NOT NULL REFERENCES tb_company (id)
);

CREATE TABLE tb_job_position (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500)
);

CREATE TABLE tb_employee (
    id UUID PRIMARY KEY,
    cpf VARCHAR(11) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    email VARCHAR(254) NOT NULL
);

CREATE TABLE tb_employment_contract (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES tb_employee (id),
    company_id UUID NOT NULL REFERENCES tb_company (id),
    department_id UUID NOT NULL REFERENCES tb_department (id),
    job_position_id UUID NOT NULL REFERENCES tb_job_position (id),
    registration_number VARCHAR(50) NOT NULL,
    hire_date DATE NOT NULL,
    termination_date DATE,
    monthly_work_hours INTEGER NOT NULL
);

CREATE TABLE tb_salary_history (
    id UUID PRIMARY KEY,
    employment_contract_id UUID NOT NULL REFERENCES tb_employment_contract (id),
    effective_from DATE NOT NULL,
    effective_until DATE,
    salary NUMERIC(19, 2) NOT NULL
);

CREATE INDEX idx_department_company ON tb_department (company_id);
CREATE INDEX idx_contract_employee ON tb_employment_contract (employee_id);
CREATE INDEX idx_contract_company ON tb_employment_contract (company_id);
CREATE INDEX idx_contract_department ON tb_employment_contract (department_id);
CREATE INDEX idx_contract_job_position ON tb_employment_contract (job_position_id);
CREATE INDEX idx_salary_history_contract ON tb_salary_history (employment_contract_id);
