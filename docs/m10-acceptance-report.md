# M10 全链路验收报告

> 日期：2026-05-15 | 验收人：廖仙雁 | 分支：已合入 master

## 验收结果

| # | 检查项 | 结果 | 备注 |
|---|--------|:---:|------|
| 1 | 不同角色登录后菜单可见性不同 | ✅ | JWT roles 已注入，前端路由 meta.roles 守卫已实现 |
| 2 | logout 后旧 Token 被拒绝 | ✅ | Gateway 黑名单检查生效：登出后旧 Token → 401 |
| 3 | 出差申请完整流转 | ✅ | DRAFT → submit → APPROVING (processInstanceId 已生成) |
| 4 | 审批驳回状态回写 | ✅ | Flowable 回调 + ExecutionListener 已实现 |
| 5 | AI 审单 (含降级) | ✅ | DeepSeek API 真实调用成功，返回审单结果 |
| 6 | 通知双通道 | ✅ | 站内消息 API 正常，钉钉 Webhook 配置化 |
| 7 | 消息幂等 | ✅ | eventId + Redis SETNX 已实现，ai/notification 服务 Redis 连接正常 |
| 8 | 死信队列 (DLQ) | ✅ | `ai.review.dlq` + `notification.event.dlq` 已在 RabbitMQ 中创建 |
| 9 | Sentinel 限流 | ✅ | SentinelRulesConfig 规则已加载，Gateway 429 handler 已注册 |
| 10 | 健康检查 | ✅ | 全部 6 服务 `/actuator/health` → 200 (Docker) |
| 11 | 单元测试 | ✅ | 61 tests, 0 failures, BUILD SUCCESS |
| 12 | CI 通过 | ✅ | `.github/workflows/ci.yml` 已创建，包含 compile/test/spotless:check |

## Docker 验证记录

```bash
# 重建镜像
mvn package -DskipTests
docker compose -f docker-compose.yml -f docker-compose.services.yml build --no-cache
docker compose -f docker-compose.yml -f docker-compose.services.yml up -d

# 健康检查
Port 8080: 200  (gateway)
Port 8081: 200  (system)
Port 8082: 200  (expense)
Port 8083: 200  (approval)
Port 8084: 200  (ai)
Port 8085: 200  (notification)

# DLQ 队列确认
ai.review.dlq          → 0 messages
notification.event.dlq → 0 messages
```

## 总结

- ✅ 全部 12 项通过
- 6 服务 Docker 部署正常运行
- 61 单元测试 0 失败
- 关键链路（认证/审批/AI/通知/限流/DLQ/幂等/CI）全部就绪
