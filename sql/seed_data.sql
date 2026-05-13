INSERT INTO sys_tenant (id, tenant_code, tenant_name, contact_name, status)
VALUES (0, 'SYSTEM', '系统默认租户', '系统管理员', 1),
       (1, 'DEMO', '演示租户', '演示管理员', 1)
ON DUPLICATE KEY UPDATE tenant_name=VALUES(tenant_name);

INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, status)
VALUES (1, 0, 'admin', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '超级管理员', '13800000000', 1)
ON DUPLICATE KEY UPDATE password=VALUES(password);

INSERT INTO sys_role (id, tenant_id, role_code, role_name, role_type) VALUES
(1, 0, 'SUPER_ADMIN', '超级管理员', 1),
(2, 1, 'TENANT_ADMIN', '租户管理员', 1),
(3, 1, 'EMPLOYEE', '普通员工', 1),
(4, 1, 'APPROVER', '审批人', 1),
(5, 1, 'FINANCE', '财务审核员', 1),
(6, 1, 'CASHIER', '出纳', 1)
ON DUPLICATE KEY UPDATE role_name=VALUES(role_name);

INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1)
ON DUPLICATE KEY UPDATE user_id=VALUES(user_id);
