-- ============================================================
-- ExpenseFlow 数据库初始化 DDL
-- MySQL 8.0+ | 共享数据库 + tenant_id 多租户隔离
-- ============================================================

CREATE DATABASE IF NOT EXISTS expense_flow
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
USE expense_flow;

-- ============================================================
-- 一、系统服务 (system-service) — 12 张表
-- ============================================================

-- 1. 租户
CREATE TABLE sys_tenant (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_code VARCHAR(50) NOT NULL COMMENT '租户编码',
  tenant_name VARCHAR(100) NOT NULL COMMENT '租户名称',
  contact_name VARCHAR(50) DEFAULT NULL COMMENT '联系人',
  contact_phone VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-启用 0-禁用',
  expire_time DATETIME DEFAULT NULL COMMENT '过期时间',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 1-已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_code (tenant_code)
) ENGINE=InnoDB COMMENT='租户表';

-- 2. 用户
CREATE TABLE sys_user (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL COMMENT '所属租户',
  username VARCHAR(50) NOT NULL,
  password VARCHAR(255) NOT NULL COMMENT 'BCrypt 加密',
  real_name VARCHAR(50) NOT NULL COMMENT '真实姓名',
  phone VARCHAR(20) DEFAULT NULL,
  email VARCHAR(100) DEFAULT NULL,
  avatar VARCHAR(255) DEFAULT NULL,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1-启用 0-禁用',
  last_login_time DATETIME DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username_tenant (tenant_id, username),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB COMMENT='用户表';

-- 3. 角色
CREATE TABLE sys_role (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  role_code VARCHAR(50) NOT NULL COMMENT '角色编码',
  role_name VARCHAR(50) NOT NULL COMMENT '角色名称',
  role_type TINYINT NOT NULL DEFAULT 2 COMMENT '1-系统内置 2-租户自定义',
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_code_tenant (tenant_id, role_code),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB COMMENT='角色表';

-- 4. 权限（菜单+按钮+API）
CREATE TABLE sys_permission (
  id BIGINT NOT NULL AUTO_INCREMENT,
  parent_id BIGINT DEFAULT 0 COMMENT '上级权限ID',
  permission_code VARCHAR(100) NOT NULL COMMENT '权限标识 exp:system:user:create',
  permission_name VARCHAR(100) NOT NULL COMMENT '权限名称',
  permission_type TINYINT NOT NULL COMMENT '1-菜单 2-按钮 3-API',
  path VARCHAR(255) DEFAULT NULL COMMENT '路由路径/API路径',
  icon VARCHAR(100) DEFAULT NULL COMMENT '菜单图标',
  sort_order INT DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_perm_code (permission_code)
) ENGINE=InnoDB COMMENT='权限表';

-- 5. 用户角色关联
CREATE TABLE sys_user_role (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_role (user_id, role_id),
  KEY idx_user (user_id),
  KEY idx_role (role_id)
) ENGINE=InnoDB COMMENT='用户角色关联表';

-- 6. 角色权限关联
CREATE TABLE sys_role_permission (
  id BIGINT NOT NULL AUTO_INCREMENT,
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_perm (role_id, permission_id),
  KEY idx_role (role_id),
  KEY idx_perm (permission_id)
) ENGINE=InnoDB COMMENT='角色权限关联表';

-- 7. 部门
CREATE TABLE sys_department (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  parent_id BIGINT DEFAULT 0 COMMENT '上级部门ID',
  dept_name VARCHAR(50) NOT NULL,
  dept_code VARCHAR(50) DEFAULT NULL,
  leader_id BIGINT DEFAULT NULL COMMENT '部门负责人(关联sys_user)',
  sort_order INT DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_tenant (tenant_id),
  KEY idx_parent (parent_id)
) ENGINE=InnoDB COMMENT='部门表';

-- 8. 员工（部门成员）
CREATE TABLE sys_employee (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  department_id BIGINT NOT NULL,
  employee_no VARCHAR(50) DEFAULT NULL COMMENT '工号',
  position VARCHAR(50) DEFAULT NULL COMMENT '职位',
  hire_date DATE DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_tenant (tenant_id, user_id),
  KEY idx_dept (department_id)
) ENGINE=InnoDB COMMENT='员工表';

-- 9. 字典类型
CREATE TABLE sys_dict_type (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT DEFAULT 0 COMMENT '0=系统级 其他=租户级',
  dict_code VARCHAR(50) NOT NULL,
  dict_name VARCHAR(50) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_dict_code_tenant (tenant_id, dict_code)
) ENGINE=InnoDB COMMENT='字典类型表';

-- 10. 字典数据
CREATE TABLE sys_dict_data (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL DEFAULT 0,
  dict_type_id BIGINT NOT NULL,
  dict_label VARCHAR(100) NOT NULL COMMENT '标签',
  dict_value VARCHAR(100) NOT NULL COMMENT '值',
  sort_order INT DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_dict_type (dict_type_id)
) ENGINE=InnoDB COMMENT='字典数据表';

-- 11. 操作审计日志
CREATE TABLE sys_audit_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  user_id BIGINT DEFAULT NULL,
  username VARCHAR(50) DEFAULT NULL,
  operation VARCHAR(50) NOT NULL COMMENT '操作类型: INSERT/UPDATE/DELETE/EXPORT',
  module VARCHAR(50) DEFAULT NULL COMMENT '模块',
  target_type VARCHAR(50) DEFAULT NULL COMMENT '操作对象类型',
  target_id VARCHAR(100) DEFAULT NULL COMMENT '操作对象ID',
  request_params TEXT COMMENT '请求参数(脱敏后)',
  old_value TEXT COMMENT '变更前值',
  new_value TEXT COMMENT '变更后值',
  ip VARCHAR(50) DEFAULT NULL,
  user_agent VARCHAR(500) DEFAULT NULL,
  duration BIGINT DEFAULT NULL COMMENT '执行时长(ms)',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_tenant_time (tenant_id, create_time),
  KEY idx_user (user_id),
  KEY idx_module (module)
) ENGINE=InnoDB COMMENT='操作审计日志表';

-- 12. OAuth2 用户关联
CREATE TABLE sys_oauth_user (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL COMMENT '关联的本地用户ID',
  provider VARCHAR(20) NOT NULL COMMENT 'OAuth2 提供方: dingtalk',
  open_id VARCHAR(100) NOT NULL,
  union_id VARCHAR(100) DEFAULT NULL,
  access_token VARCHAR(500) DEFAULT NULL,
  refresh_token VARCHAR(500) DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_provider_openid (provider, open_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='OAuth2用户关联表';

-- ============================================================
-- 二、差旅报销服务 (expense-service) — 7 张表
-- ============================================================

-- 13. 出差申请
CREATE TABLE ex_travel_request (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  request_no VARCHAR(50) NOT NULL COMMENT '业务编号: TR-YYYYMMDD-XXXX',
  applicant_id BIGINT NOT NULL COMMENT '申请人(sys_user.id)',
  department_id BIGINT DEFAULT NULL COMMENT '申请部门',
  travel_purpose VARCHAR(500) NOT NULL COMMENT '出差目的',
  destination VARCHAR(200) NOT NULL COMMENT '目的地',
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  estimated_amount DECIMAL(12,2) DEFAULT 0.00 COMMENT '预估费用',
  companions VARCHAR(500) DEFAULT NULL COMMENT '同行人',
  remark VARCHAR(1000) DEFAULT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/SUBMITTED/APPROVING/APPROVED/REJECTED/WITHDRAWN/CHANGED',
  process_instance_id VARCHAR(64) DEFAULT NULL COMMENT 'Flowable 流程实例ID',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_request_no (request_no),
  KEY idx_tenant_applicant (tenant_id, applicant_id),
  KEY idx_status (status),
  KEY idx_process (process_instance_id)
) ENGINE=InnoDB COMMENT='出差申请表';

-- 14. 报销单
CREATE TABLE ex_expense_report (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  report_no VARCHAR(50) NOT NULL COMMENT '业务编号: ER-YYYYMMDD-XXXX',
  applicant_id BIGINT NOT NULL,
  department_id BIGINT DEFAULT NULL,
  travel_request_id BIGINT DEFAULT NULL COMMENT '关联出差申请',
  total_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '报销总额',
  actual_amount DECIMAL(12,2) DEFAULT 0.00 COMMENT '实报金额',
  report_date DATE NOT NULL COMMENT '报销日期',
  remark VARCHAR(1000) DEFAULT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/SUBMITTED/APPROVING/APPROVED/REJECTED/WITHDRAWN/PAID',
  process_instance_id VARCHAR(64) DEFAULT NULL,
  paid_amount DECIMAL(12,2) DEFAULT NULL COMMENT '打款金额',
  paid_time DATETIME DEFAULT NULL COMMENT '打款时间',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_report_no (report_no),
  KEY idx_tenant_applicant (tenant_id, applicant_id),
  KEY idx_travel (travel_request_id),
  KEY idx_status (status),
  KEY idx_process (process_instance_id)
) ENGINE=InnoDB COMMENT='报销单表';

-- 15. 报销明细项
CREATE TABLE ex_expense_item (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  report_id BIGINT NOT NULL COMMENT '关联报销单',
  expense_type VARCHAR(20) NOT NULL COMMENT '费用类型: TRANSPORT/HOTEL/MEAL/OTHER',
  expense_date DATE NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  description VARCHAR(500) DEFAULT NULL,
  invoice_id BIGINT DEFAULT NULL COMMENT '关联发票',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_report (report_id),
  KEY idx_invoice (invoice_id)
) ENGINE=InnoDB COMMENT='报销明细项表';

-- 16. 发票
CREATE TABLE ex_invoice (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  invoice_no VARCHAR(50) DEFAULT NULL COMMENT '发票号码',
  invoice_code VARCHAR(50) DEFAULT NULL COMMENT '发票代码',
  invoice_type VARCHAR(20) NOT NULL COMMENT 'VAT_SPECIAL/VAT_NORMAL/ELECTRONIC',
  invoice_date DATE DEFAULT NULL,
  amount DECIMAL(10,2) DEFAULT NULL COMMENT '不含税金额',
  tax_amount DECIMAL(10,2) DEFAULT NULL COMMENT '税额',
  total_amount DECIMAL(10,2) DEFAULT NULL COMMENT '价税合计',
  seller_name VARCHAR(200) DEFAULT NULL COMMENT '销售方名称',
  seller_tax_no VARCHAR(50) DEFAULT NULL COMMENT '销售方税号',
  buyer_name VARCHAR(200) DEFAULT NULL COMMENT '购买方名称',
  buyer_tax_no VARCHAR(50) DEFAULT NULL COMMENT '购买方税号',
  image_url VARCHAR(500) DEFAULT NULL COMMENT '发票图片URL',
  ocr_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/SUCCESS/FAILED',
  ocr_raw_result TEXT COMMENT 'OCR 原始返回',
  ocr_confidence DECIMAL(5,4) DEFAULT NULL COMMENT 'OCR 置信度',
  verify_status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/VERIFIED/INVALID',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_invoice_no_code (invoice_no, invoice_code),
  KEY idx_tenant (tenant_id),
  KEY idx_ocr_status (ocr_status)
) ENGINE=InnoDB COMMENT='发票表';

-- 17. 消费记录
CREATE TABLE ex_cost_record (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  cost_date DATE NOT NULL,
  cost_type VARCHAR(20) NOT NULL COMMENT '费用类型',
  amount DECIMAL(10,2) NOT NULL,
  description VARCHAR(500) DEFAULT NULL,
  invoice_id BIGINT DEFAULT NULL,
  travel_request_id BIGINT DEFAULT NULL,
  report_id BIGINT DEFAULT NULL COMMENT '关联的报销单',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_tenant_user (tenant_id, user_id),
  KEY idx_report (report_id),
  KEY idx_travel (travel_request_id)
) ENGINE=InnoDB COMMENT='消费记录表';

-- 18. 打款流水
CREATE TABLE ex_payment_record (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  report_id BIGINT NOT NULL,
  payment_no VARCHAR(50) NOT NULL COMMENT '打款流水号: PY-YYYYMMDD-XXXX',
  payee_name VARCHAR(50) NOT NULL COMMENT '收款人',
  payee_account VARCHAR(50) DEFAULT NULL COMMENT '收款账号(脱敏)',
  amount DECIMAL(12,2) NOT NULL COMMENT '打款金额',
  payment_method VARCHAR(20) DEFAULT 'BANK_TRANSFER' COMMENT '打款方式',
  payment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/SUCCESS/FAILED',
  payment_time DATETIME DEFAULT NULL,
  operator_id BIGINT NOT NULL COMMENT '出纳(sys_user.id)',
  remark VARCHAR(500) DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_payment_no (payment_no),
  KEY idx_report (report_id),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB COMMENT='打款流水表';

-- 19. 费用政策
CREATE TABLE ex_expense_policy (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  policy_name VARCHAR(100) NOT NULL,
  expense_type VARCHAR(20) NOT NULL COMMENT '费用类型',
  max_amount DECIMAL(10,2) NOT NULL COMMENT '单次报销上限',
  daily_limit DECIMAL(10,2) DEFAULT NULL COMMENT '日限额',
  city_tier VARCHAR(10) DEFAULT NULL COMMENT '适用城市等级: TIER1/TIER2/TIER3',
  effective_date DATE NOT NULL,
  expire_date DATE DEFAULT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  remark VARCHAR(500) DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_tenant_type (tenant_id, expense_type)
) ENGINE=InnoDB COMMENT='费用政策表';

-- ============================================================
-- 三、审批引擎服务 (approval-service) — 1 张业务表
-- (Flowable 工作流引擎的 60+ 张表由框架自动创建，此处仅建业务记录表)
-- ============================================================

-- 20. 审批记录（业务维度双写，便于查询和报表）
CREATE TABLE ap_approval_record (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  business_type VARCHAR(20) NOT NULL COMMENT 'TRAVEL_REQUEST/EXPENSE_REPORT',
  business_id BIGINT NOT NULL COMMENT '业务单据ID',
  process_instance_id VARCHAR(64) NOT NULL COMMENT 'Flowable 流程实例ID',
  task_id VARCHAR(64) DEFAULT NULL COMMENT 'Flowable 任务ID',
  task_name VARCHAR(100) DEFAULT NULL COMMENT '任务名称(审批节点)',
  approver_id BIGINT NOT NULL COMMENT '审批人',
  approver_name VARCHAR(50) DEFAULT NULL,
  action VARCHAR(20) NOT NULL COMMENT 'APPROVE/REJECT/DELEGATE/ADD_SIGN/RETURN',
  comment VARCHAR(1000) DEFAULT NULL COMMENT '审批意见',
  action_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_business (business_type, business_id),
  KEY idx_process (process_instance_id),
  KEY idx_approver (approver_id),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB COMMENT='审批记录表';

-- ============================================================
-- 四、AI 智能服务 (ai-service) — 3 张表
-- ============================================================

-- 21. OCR 识别结果
CREATE TABLE ai_ocr_result (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  invoice_id BIGINT NOT NULL COMMENT '关联发票(ex_invoice.id)',
  request_id VARCHAR(100) DEFAULT NULL COMMENT '阿里云 OCR 请求ID',
  raw_response TEXT COMMENT 'API 原始响应JSON',
  parsed_invoice_no VARCHAR(50) DEFAULT NULL,
  parsed_invoice_code VARCHAR(50) DEFAULT NULL,
  parsed_amount DECIMAL(10,2) DEFAULT NULL,
  parsed_invoice_date DATE DEFAULT NULL,
  parsed_seller_name VARCHAR(200) DEFAULT NULL,
  parsed_seller_tax_no VARCHAR(50) DEFAULT NULL,
  parsed_buyer_name VARCHAR(200) DEFAULT NULL,
  parsed_buyer_tax_no VARCHAR(50) DEFAULT NULL,
  confidence DECIMAL(5,4) DEFAULT NULL COMMENT '综合置信度',
  status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' COMMENT 'SUCCESS/FAILED',
  error_message VARCHAR(500) DEFAULT NULL,
  process_time_ms BIGINT DEFAULT NULL COMMENT 'OCR 耗时(ms)',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_invoice (invoice_id),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB COMMENT='OCR识别结果表';

-- 22. AI 审单结果
CREATE TABLE ai_review_result (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  business_type VARCHAR(20) NOT NULL COMMENT 'EXPENSE_REPORT/TRAVEL_REQUEST',
  business_id BIGINT NOT NULL COMMENT '业务单据ID',
  model VARCHAR(50) NOT NULL DEFAULT 'deepseek-chat' COMMENT '调用的模型',
  prompt_tokens INT DEFAULT 0,
  completion_tokens INT DEFAULT 0,
  review_result VARCHAR(20) NOT NULL COMMENT 'APPROVED/REVIEW_NEEDED/REJECTED',
  risk_level VARCHAR(10) DEFAULT 'LOW' COMMENT 'LOW/MEDIUM/HIGH',
  review_opinion TEXT COMMENT 'AI 审单意见',
  risk_reasons TEXT COMMENT '风险原因列表(JSON数组)',
  confidence DECIMAL(5,4) DEFAULT NULL COMMENT 'AI 置信度',
  process_time_ms BIGINT DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_business (business_type, business_id),
  KEY idx_tenant (tenant_id),
  KEY idx_result (review_result)
) ENGINE=InnoDB COMMENT='AI审单结果表';

-- 23. AI 置信度统计
CREATE TABLE ai_confidence_stats (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  stat_date DATE NOT NULL COMMENT '统计日期',
  total_reviews INT DEFAULT 0 COMMENT '审单总数',
  auto_approved INT DEFAULT 0 COMMENT '自动通过数',
  manual_approved INT DEFAULT 0 COMMENT '人工通过数',
  rejected INT DEFAULT 0 COMMENT '驳回数',
  ai_advice_adopted INT DEFAULT 0 COMMENT 'AI建议采纳数',
  ai_advice_overridden INT DEFAULT 0 COMMENT 'AI建议被推翻数',
  avg_confidence DECIMAL(5,4) DEFAULT NULL COMMENT '平均置信度',
  avg_process_time_ms BIGINT DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_date (tenant_id, stat_date)
) ENGINE=InnoDB COMMENT='AI置信度统计表';

-- ============================================================
-- 五、通知服务 (notification-service) — 2 张表
-- ============================================================

-- 24. 站内消息
CREATE TABLE nt_message (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL COMMENT '接收人',
  message_type VARCHAR(20) NOT NULL DEFAULT 'NOTIFICATION' COMMENT 'SYSTEM/APPROVAL/NOTIFICATION',
  title VARCHAR(200) NOT NULL,
  content TEXT COMMENT '消息内容',
  business_type VARCHAR(20) DEFAULT NULL COMMENT '关联业务类型',
  business_id BIGINT DEFAULT NULL COMMENT '关联业务ID',
  is_read TINYINT NOT NULL DEFAULT 0,
  read_time DATETIME DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_tenant_user_read (tenant_id, user_id, is_read),
  KEY idx_create_time (create_time)
) ENGINE=InnoDB COMMENT='站内消息表';

-- 25. 通知模板
CREATE TABLE nt_notification_template (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '0=系统默认模板',
  template_code VARCHAR(50) NOT NULL,
  template_name VARCHAR(100) NOT NULL,
  channel VARCHAR(20) NOT NULL COMMENT 'DINGTALK/IN_APP/EMAIL',
  title_template VARCHAR(200) NOT NULL COMMENT '标题模板(支持变量占位)',
  content_template TEXT NOT NULL COMMENT '内容模板(支持变量占位)',
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_template_code (tenant_id, template_code)
) ENGINE=InnoDB COMMENT='通知模板表';

-- ============================================================
-- 种子数据：默认租户 + 超级管理员
-- ============================================================

INSERT INTO sys_tenant (id, tenant_code, tenant_name, contact_name, status)
VALUES (0, 'SYSTEM', '系统默认租户', '系统管理员', 1),
       (1, 'DEMO', '演示租户', '演示管理员', 1);

INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, status)
VALUES (1, 0, 'admin', '$2b$10$dEJWjVMLopY.XeLwRHaDoeP1RiTwXhSgWsqvmXzU.TfgBoe4yh7iq', '超级管理员', '13800000000', 1);
-- 默认密码: admin123 (BCrypt 在线生成替换)

INSERT INTO sys_role (id, tenant_id, role_code, role_name, role_type)
VALUES (1, 0, 'SUPER_ADMIN', '超级管理员', 1),
       (2, 0, 'TENANT_ADMIN', '租户管理员', 1),
       (3, 0, 'EMPLOYEE',     '普通员工',   1),
       (4, 0, 'APPROVER',     '审批人',     1),
       (5, 0, 'FINANCE',      '财务审核员',  1),
       (6, 0, 'CASHIER',      '出纳',       1);

INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);

INSERT INTO nt_notification_template (id, tenant_id, template_code, template_name, channel, title_template, content_template, status, create_time)
VALUES (1, 0, 'APPROVAL_PENDING', '审批待办通知', 'DINGTALK',
        '【审批待办】您有一条新的{业务类型}待审批',
        '申请人：{申请人}\n单据编号：{单据编号}\n金额：{金额}元\n提交时间：{提交时间}\n请及时处理。', 1, NOW()),
       (2, 0, 'APPROVAL_RESULT', '审批结果通知', 'DINGTALK',
        '【审批结果】您的{业务类型}已{审批结果}',
        '单据编号：{单据编号}\n审批人：{审批人}\n审批意见：{审批意见}\n审批时间：{审批时间}', 1, NOW()),
       (3, 0, 'AI_REVIEW_DONE', 'AI审单完成', 'IN_APP',
        'AI 审单结果',
        '报销单 {编号} 的 AI 审单已完成，结果：{结果}，风险等级：{风险等级}', 1, NOW());

-- 演示用户（密码均为 admin123，BCrypt 加密）
INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, status)
VALUES (2, 0, 'manager', '$2b$10$dEJWjVMLopY.XeLwRHaDoeP1RiTwXhSgWsqvmXzU.TfgBoe4yh7iq', '张经理', '13800000001', 1),
       (3, 0, 'director', '$2b$10$dEJWjVMLopY.XeLwRHaDoeP1RiTwXhSgWsqvmXzU.TfgBoe4yh7iq', '李总监', '13800000002', 1),
       (4, 0, 'finance', '$2b$10$dEJWjVMLopY.XeLwRHaDoeP1RiTwXhSgWsqvmXzU.TfgBoe4yh7iq', '王财务', '13800000003', 1),
       (5, 0, 'cashier', '$2b$10$dEJWjVMLopY.XeLwRHaDoeP1RiTwXhSgWsqvmXzU.TfgBoe4yh7iq', '赵出纳', '13800000004', 1);

INSERT INTO sys_user_role (user_id, role_id) VALUES
(2, 4),  -- manager → APPROVER
(3, 4),  -- director → APPROVER
(4, 5),  -- finance → FINANCE
(5, 6);  -- cashier → CASHIER

-- ============================================================
-- 7. 权限种子数据（53 条）
-- ============================================================
-- 菜单权限（13 条，parent_id=0）
INSERT INTO sys_permission (id, parent_id, permission_code, permission_name, permission_type, path, icon, sort_order) VALUES
(101, 0, 'dashboard',        '工作台',       1, '/dashboard',       'DataAnalysis', 1),
(102, 0, 'travel',           '差旅出行',     1, '/travel',          'Promotion',    2),
(103, 0, 'report',           '费用报销',     1, '/report',          'Document',     3),
(104, 0, 'invoice',          '票据管理',     1, '/invoice',         'Picture',      4),
(105, 0, 'approval',         '审批中心',     1, '/approval',        'Checked',      5),
(106, 0, 'ai:review',        '智能审单',     1, '/ai-review',       'Cpu',          6),
(107, 0, 'ai:assistant',     '政策问答',     1, '/ai-assistant',    'ChatDotRound', 7),
(108, 0, 'notification',     '消息中心',     1, '/notification',    'Bell',         8),
(109, 0, 'system:user',      '用户管理',     1, '/system/user',     'User',         9),
(110, 0, 'system:role',      '角色管理',     1, '/system/role',     'Avatar',       10),
(111, 0, 'system:tenant',    '租户管理',     1, '/system/tenant',   'OfficeBuilding',11),
(112, 0, 'finance:payment',  '打款管理',     1, '/payment',         'Money',        12),
(113, 0, 'finance:policy',   '费用政策',     1, '/finance/policy',  'Tickets',      13);

-- 操作权限（40 条，parent_id 指向对应菜单）
INSERT INTO sys_permission (id, parent_id, permission_code, permission_name, permission_type, path, icon, sort_order) VALUES
-- dashboard
(201, 101, 'dashboard:view',     '查看工作台',     2, NULL, NULL, 1),
-- travel
(202, 102, 'travel:create',      '新建出差',       2, NULL, NULL, 1),
(203, 102, 'travel:view',        '查看出差列表',   2, NULL, NULL, 2),
(204, 102, 'travel:edit',        '编辑出差',       2, NULL, NULL, 3),
(205, 102, 'travel:delete',      '删除出差',       2, NULL, NULL, 4),
-- report
(206, 103, 'report:create',      '创建报销单',     2, NULL, NULL, 1),
(207, 103, 'report:view',        '查看报销列表',   2, NULL, NULL, 2),
(208, 103, 'report:edit',        '编辑报销单',     2, NULL, NULL, 3),
(209, 103, 'report:delete',      '删除报销单',     2, NULL, NULL, 4),
(210, 103, 'report:submit',      '提交报销单',     2, NULL, NULL, 5),
(211, 103, 'report:withdraw',    '撤回报销单',     2, NULL, NULL, 6),
-- invoice
(212, 104, 'invoice:upload',     '上传发票',       2, NULL, NULL, 1),
(213, 104, 'invoice:view',       '查看发票列表',   2, NULL, NULL, 2),
(214, 104, 'invoice:delete',     '删除发票',       2, NULL, NULL, 3),
-- ocr
(215, 104, 'ocr:recognize',      '触发OCR识别',    2, NULL, NULL, 4),
(216, 104, 'ocr:result',         '查看OCR结果',    2, NULL, NULL, 5),
-- approval
(217, 105, 'approval:view',      '查看待办列表',   2, NULL, NULL, 1),
(218, 105, 'approval:approve',   '审批通过',       2, NULL, NULL, 2),
(219, 105, 'approval:reject',    '审批驳回',       2, NULL, NULL, 3),
(220, 105, 'approval:delegate',  '委派审批',       2, NULL, NULL, 4),
-- payment
(221, 112, 'payment:create',     '发起打款',       2, NULL, NULL, 1),
(222, 112, 'payment:confirm',    '确认打款',       2, NULL, NULL, 2),
(223, 112, 'payment:view',       '查看打款记录',   2, NULL, NULL, 3),
-- policy
(224, 113, 'policy:create',      '创建费用政策',   2, NULL, NULL, 1),
(225, 113, 'policy:edit',        '编辑费用政策',   2, NULL, NULL, 2),
(226, 113, 'policy:delete',      '删除费用政策',   2, NULL, NULL, 3),
(227, 113, 'policy:view',        '查看费用政策',   2, NULL, NULL, 4),
-- user
(228, 109, 'user:create',        '创建用户',       2, NULL, NULL, 1),
(229, 109, 'user:edit',          '编辑用户',       2, NULL, NULL, 2),
(230, 109, 'user:delete',        '删除用户',       2, NULL, NULL, 3),
(231, 109, 'user:view',          '查看用户列表',   2, NULL, NULL, 4),
(232, 109, 'user:assignRole',    '分配角色',       2, NULL, NULL, 5),
-- role
(233, 110, 'role:create',        '创建角色',       2, NULL, NULL, 1),
(234, 110, 'role:edit',          '编辑角色',       2, NULL, NULL, 2),
(235, 110, 'role:view',          '查看角色列表',   2, NULL, NULL, 3),
(236, 110, 'role:assignPerm',    '分配权限',       2, NULL, NULL, 4),
-- tenant
(237, 111, 'tenant:create',      '创建租户',       2, NULL, NULL, 1),
(238, 111, 'tenant:edit',        '编辑租户',       2, NULL, NULL, 2),
(239, 111, 'tenant:view',        '查看租户列表',   2, NULL, NULL, 3),
-- notification
(240, 108, 'notification:manage','管理通知模板',   2, NULL, NULL, 1),
(241, 108, 'notification:send',  '手动发送通知',   2, NULL, NULL, 2),
-- ai
(242, 106, 'ai:review:execute',  '执行AI审单',     2, NULL, NULL, 1),
(243, 106, 'ai:review:result',   '查看AI审单结果', 2, NULL, NULL, 2),
(244, 107, 'ai:rag:query',       'RAG政策问答',    2, NULL, NULL, 1);

-- ============================================================
-- 8. 角色-权限关联种子数据
-- ============================================================
-- SUPER_ADMIN (role_id=1): 拥有全部 53 个权限
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(1,101),(1,102),(1,103),(1,104),(1,105),(1,106),(1,107),(1,108),(1,109),(1,110),(1,111),(1,112),(1,113),
(1,201),(1,202),(1,203),(1,204),(1,205),
(1,206),(1,207),(1,208),(1,209),(1,210),(1,211),
(1,212),(1,213),(1,214),(1,215),(1,216),
(1,217),(1,218),(1,219),(1,220),
(1,221),(1,222),(1,223),
(1,224),(1,225),(1,226),(1,227),
(1,228),(1,229),(1,230),(1,231),(1,232),
(1,233),(1,234),(1,235),(1,236),
(1,237),(1,238),(1,239),
(1,240),(1,241),
(1,242),(1,243),(1,244);

-- TENANT_ADMIN (role_id=2): 不含 system:tenant 菜单和 tenant:create/edit
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(2,101),(2,102),(2,103),(2,104),(2,105),(2,106),(2,107),(2,108),(2,109),(2,110),(2,112),(2,113),
(2,201),(2,202),(2,203),(2,204),(2,205),
(2,206),(2,207),(2,208),(2,209),(2,210),(2,211),
(2,212),(2,213),(2,214),(2,215),(2,216),
(2,217),(2,218),(2,219),(2,220),
(2,223),
(2,224),(2,225),(2,226),(2,227),
(2,228),(2,229),(2,230),(2,231),(2,232),
(2,233),(2,234),(2,235),(2,236),
(2,239),
(2,240),(2,241),
(2,242),(2,243),(2,244);

-- EMPLOYEE (role_id=3): 只能做自己的差旅/报销/发票/OCR
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(3,101),(3,102),(3,103),(3,104),(3,107),(3,108),
(3,201),(3,202),(3,203),(3,204),(3,205),
(3,206),(3,207),(3,208),(3,209),(3,210),(3,211),
(3,212),(3,213),(3,214),(3,215),(3,216),
(3,227),
(3,244);

-- APPROVER (role_id=4): 审批操作 + 可查看差旅/报销/政策 + AI问答/结果
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(4,101),(4,105),(4,107),(4,108),
(4,201),(4,203),
(4,207),
(4,217),(4,218),(4,219),(4,220),
(4,227),
(4,243),(4,244);

-- FINANCE (role_id=5): 财务审核员 — 审批+发票查看+OCR+费用政策+AI审单
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(5,101),(5,105),(5,106),(5,107),(5,108),(5,113),
(5,201),(5,203),
(5,207),
(5,213),(5,215),(5,216),
(5,217),(5,218),(5,219),
(5,223),(5,224),(5,225),(5,226),(5,227),
(5,242),(5,243),(5,244);

-- CASHIER (role_id=6): 只管打款 + 收消息 + 看报销单
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(6,101),(6,108),(6,112),
(6,201),
(6,207),
(6,221),(6,222),(6,223);

-- 费用政策（演示用）
INSERT INTO ex_expense_policy (tenant_id, policy_name, expense_type, max_amount, daily_limit, city_tier, effective_date, expire_date, status, create_time) VALUES
(0, '交通费标准', 'TRANSPORT', 5000.00, NULL, 'TIER1', '2026-01-01', '2027-12-31', 1, NOW()),
(0, '住宿费标准-一线', 'HOTEL', 500.00, 500.00, 'TIER1', '2026-01-01', '2027-12-31', 1, NOW()),
(0, '住宿费标准-其他', 'HOTEL', 350.00, 350.00, 'TIER2', '2026-01-01', '2027-12-31', 1, NOW()),
(0, '餐费补助', 'MEAL', 100.00, 100.00, 'TIER1', '2026-01-01', '2027-12-31', 1, NOW());

-- 通知模板（P1-3 钉钉推送）
INSERT INTO nt_notification_template (tenant_id, template_code, template_name, channel, title_template, content_template, status, create_time) VALUES
(0, 'DING_REPORT_SUBMITTED', '报销提交通知', 'DINGTALK',
 '📄 报销申请已提交',
 '**申请人：** {applicantName}\n**单号：** {reportNo}\n**金额：** {amount} 元\n**时间：** {submitTime}',
 1, NOW()),

(0, 'DING_APPROVAL_RESULT', '审批结果通知', 'DINGTALK',
 '📋 审批结果：{outcome}',
 '**单号：** {requestNo}\n**类型：** {businessType}\n**结果：** {outcome}\n**操作人：** {operator}',
 1, NOW()),

(0, 'DING_PAYMENT_COMPLETED', '打款完成通知', 'DINGTALK',
 '💰 报销款已到账',
 '**单号：** {reportNo}\n**金额：** {amount} 元\n**打款时间：** {paidTime}',
 1, NOW()),

(0, 'DING_REPORT_WITHDRAWN', '报销撤回通知', 'DINGTALK',
 '↩️ 报销申请已撤回',
 '**单号：** {reportNo}\n**撤回人：** {applicantName}\n**原状态：** {previousStatus}',
 1, NOW());
