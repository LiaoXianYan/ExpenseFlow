CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  username VARCHAR(50) NOT NULL,
  password VARCHAR(255) NOT NULL,
  real_name VARCHAR(50) NOT NULL,
  phone VARCHAR(20) DEFAULT NULL,
  email VARCHAR(100) DEFAULT NULL,
  avatar VARCHAR(255) DEFAULT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  last_login_time DATETIME DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username_tenant (tenant_id, username)
);

CREATE TABLE IF NOT EXISTS sys_role (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  role_code VARCHAR(50) NOT NULL,
  role_name VARCHAR(50) NOT NULL,
  role_type TINYINT NOT NULL DEFAULT 2,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_code_tenant (tenant_id, role_code)
);

CREATE TABLE IF NOT EXISTS sys_user_role (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_role (user_id, role_id)
);

-- seed roles
INSERT INTO sys_role (id, tenant_id, role_code, role_name, role_type, status, create_time)
VALUES (1, 0, 'SUPER_ADMIN', '超级管理员', 1, 1, NOW()),
       (2, 0, 'TENANT_ADMIN', '租户管理员', 1, 1, NOW()),
       (3, 0, 'EMPLOYEE',     '普通员工',   1, 1, NOW()),
       (4, 0, 'APPROVER',     '审批人',     1, 1, NOW()),
       (5, 0, 'FINANCE',      '财务审核员',  1, 1, NOW()),
       (6, 0, 'CASHIER',      '出纳',       1, 1, NOW());

-- seed admin user (password: admin123, BCrypt)
INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, status, create_time)
VALUES (1, 0, 'admin', '$2b$10$dEJWjVMLopY.XeLwRHaDoeP1RiTwXhSgWsqvmXzU.TfgBoe4yh7iq', '超级管理员', '13800000000', 1, NOW()),
       (2, 0, 'manager', '$2b$10$dEJWjVMLopY.XeLwRHaDoeP1RiTwXhSgWsqvmXzU.TfgBoe4yh7iq', '张经理', '13800000001', 1, NOW()),
       (3, 0, 'director', '$2b$10$dEJWjVMLopY.XeLwRHaDoeP1RiTwXhSgWsqvmXzU.TfgBoe4yh7iq', '李总监', '13800000002', 1, NOW());

-- admin → SUPER_ADMIN
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);
INSERT INTO sys_user_role (user_id, role_id) VALUES (2, 4);
INSERT INTO sys_user_role (user_id, role_id) VALUES (3, 4);
