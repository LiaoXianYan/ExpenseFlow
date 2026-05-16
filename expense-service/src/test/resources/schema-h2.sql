-- H2 schema for expense-service integration tests
-- MySQL-compatible mode, COMMENT / ENGINE / ON UPDATE removed

CREATE TABLE IF NOT EXISTS ex_expense_report (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  report_no VARCHAR(50) NOT NULL,
  applicant_id BIGINT NOT NULL,
  department_id BIGINT DEFAULT NULL,
  travel_request_id BIGINT DEFAULT NULL,
  total_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  actual_amount DECIMAL(12,2) DEFAULT 0.00,
  report_date DATE NOT NULL,
  remark VARCHAR(1000) DEFAULT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  process_instance_id VARCHAR(64) DEFAULT NULL,
  paid_amount DECIMAL(12,2) DEFAULT NULL,
  paid_time DATETIME DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_report_no (report_no)
);

CREATE TABLE IF NOT EXISTS ex_expense_item (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  report_id BIGINT NOT NULL,
  expense_type VARCHAR(20) NOT NULL,
  expense_date DATE NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  description VARCHAR(500) DEFAULT NULL,
  invoice_id BIGINT DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS ex_expense_policy (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  policy_name VARCHAR(100) NOT NULL,
  expense_type VARCHAR(20) NOT NULL,
  max_amount DECIMAL(10,2) NOT NULL,
  daily_limit DECIMAL(10,2) DEFAULT NULL,
  city_tier VARCHAR(10) DEFAULT NULL,
  effective_date DATE NOT NULL,
  expire_date DATE DEFAULT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  remark VARCHAR(500) DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);
