-- H2 schema for approval-service integration tests
-- MySQL-compatible mode, COMMENT / ENGINE / ON UPDATE removed

CREATE TABLE IF NOT EXISTS ap_approval_record (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  business_type VARCHAR(20) NOT NULL,
  business_id BIGINT NOT NULL,
  process_instance_id VARCHAR(64) NOT NULL,
  task_id VARCHAR(64) DEFAULT NULL,
  task_name VARCHAR(100) DEFAULT NULL,
  approver_id BIGINT NOT NULL,
  approver_name VARCHAR(50) DEFAULT NULL,
  action VARCHAR(20) NOT NULL,
  comment VARCHAR(1000) DEFAULT NULL,
  action_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
