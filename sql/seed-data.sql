-- ============================================================
-- ExpenseFlow 演示种子数据
-- 在 init.sql 执行后运行
-- ============================================================

-- 费用政策 (演示用)
INSERT INTO ex_expense_policy (tenant_id, policy_name, expense_type, max_amount, daily_limit, city_tier, effective_date, expire_date, status, create_time) VALUES
(0, '交通费标准', 'TRANSPORT', 5000.00, NULL, 'TIER1', '2026-01-01', '2027-12-31', 1, NOW()),
(0, '住宿费标准-一线', 'HOTEL', 500.00, 500.00, 'TIER1', '2026-01-01', '2027-12-31', 1, NOW()),
(0, '住宿费标准-其他', 'HOTEL', 350.00, 350.00, 'TIER2', '2026-01-01', '2027-12-31', 1, NOW()),
(0, '餐费补助', 'MEAL', 100.00, 100.00, 'TIER1', '2026-01-01', '2027-12-31', 1, NOW());

-- 通知模板 (演示用)
INSERT INTO nt_notification_template (tenant_id, template_code, template_name, channel, title_template, content_template, status, create_time) VALUES
(0, 'APPROVAL_PENDING', '待审批通知', 'DINGTALK', '【审批待办】您有一条新的{业务类型}待审批', '申请人：{申请人}\n金额：{金额}元\n请及时处理', 1, NOW()),
(0, 'APPROVAL_RESULT', '审批结果通知', 'DINGTALK', '【审批结果】您的{业务类型}已{审批结果}', '{业务类型}编号：{编号}\n审批结果：{审批结果}\n审批意见：{意见}', 1, NOW()),
(0, 'AI_REVIEW_DONE', 'AI审单完成', 'IN_APP', 'AI 审单结果', '报销单 {编号} 的 AI 审单已完成，结果：{结果}，风险等级：{风险等级}', 1, NOW());
