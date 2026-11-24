UPDATE tb_company
SET cnpj = UPPER(TRANSLATE(BTRIM(cnpj), './-', ''))
WHERE cnpj IS NOT NULL;

ALTER TABLE tb_company
    ALTER COLUMN cnpj SET NOT NULL,
    ADD CONSTRAINT ck_company_cnpj_format CHECK (cnpj ~ '^[A-Z0-9]{12}[0-9]{2}$'),
    ADD CONSTRAINT uk_company_cnpj UNIQUE (cnpj);
