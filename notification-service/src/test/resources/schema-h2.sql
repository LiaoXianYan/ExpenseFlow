-- H2 schema for notification-service integration tests
-- MySQL-compatible mode, COMMENT / ENGINE / ON UPDATE removed

CREATE TABLE IF NOT EXISTS nt_message (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  message_type VARCHAR(20) NOT NULL DEFAULT 'NOTIFICATION',
  title VARCHAR(200) NOT NULL,
  content TEXT,
  business_type VARCHAR(20) DEFAULT NULL,
  business_id BIGINT DEFAULT NULL,
  is_read TINYINT NOT NULL DEFAULT 0,
  read_time DATETIME DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_nt_message_tenant_user_read ON nt_message(tenant_id, user_id, is_read);
CREATE INDEX IF NOT EXISTS idx_nt_message_create_time ON nt_message(create_time);

CREATE TABLE IF NOT EXISTS nt_notification_template (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL DEFAULT 0,
  template_code VARCHAR(50) NOT NULL,
  template_name VARCHAR(100) NOT NULL,
  channel VARCHAR(20) NOT NULL,
  title_template VARCHAR(200) NOT NULL,
  content_template TEXT NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_nt_template_code ON nt_notification_template(tenant_id, template_code);
