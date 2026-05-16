-- H2 schema for ai-service integration tests

CREATE TABLE IF NOT EXISTS ai_ocr_result (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  invoice_id BIGINT NOT NULL,
  request_id VARCHAR(100) DEFAULT NULL,
  raw_response TEXT,
  parsed_invoice_no VARCHAR(50) DEFAULT NULL,
  parsed_invoice_code VARCHAR(50) DEFAULT NULL,
  parsed_amount DECIMAL(10,2) DEFAULT NULL,
  parsed_invoice_date DATE DEFAULT NULL,
  parsed_seller_name VARCHAR(200) DEFAULT NULL,
  parsed_seller_tax_no VARCHAR(50) DEFAULT NULL,
  parsed_buyer_name VARCHAR(200) DEFAULT NULL,
  parsed_buyer_tax_no VARCHAR(50) DEFAULT NULL,
  confidence DECIMAL(5,4) DEFAULT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
  error_message VARCHAR(500) DEFAULT NULL,
  process_time_ms BIGINT DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_invoice (invoice_id)
);

CREATE TABLE IF NOT EXISTS ai_review_result (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  business_type VARCHAR(20) NOT NULL,
  business_id BIGINT NOT NULL,
  model VARCHAR(50) NOT NULL DEFAULT 'deepseek-chat',
  prompt_tokens INT DEFAULT 0,
  completion_tokens INT DEFAULT 0,
  review_result VARCHAR(20) NOT NULL,
  risk_level VARCHAR(10) DEFAULT 'LOW',
  review_opinion TEXT,
  risk_reasons TEXT,
  confidence DECIMAL(5,4) DEFAULT NULL,
  process_time_ms BIGINT DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS ai_confidence_stats (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  stat_date DATE NOT NULL,
  total_reviews INT DEFAULT 0,
  auto_approved INT DEFAULT 0,
  manual_approved INT DEFAULT 0,
  rejected INT DEFAULT 0,
  ai_advice_adopted INT DEFAULT 0,
  ai_advice_overridden INT DEFAULT 0,
  avg_confidence DECIMAL(5,4) DEFAULT NULL,
  avg_process_time_ms BIGINT DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_date (tenant_id, stat_date)
);
