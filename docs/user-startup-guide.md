# ExpenseFlow 用户启动手册

## 环境要求

| 软件 | 版本 | 说明 |
|------|------|------|
| JDK | 17+ | 后端编译与运行 |
| Maven | 3.6+ | 项目构建 |
| Node.js | 20+ | 前端构建（方式二/三需要） |
| Docker | 24+ | 中间件编排 |
| Docker Compose | v2 | 服务编排 |

---

## 一、Docker 一键全栈启动（推荐）

最简方式，一条命令启动全部 13 个容器。

```bash
# 1. 编译
cd ExpenseFlow
mvn clean package -DskipTests

# 2. 启动（中间件 + 6 微服务 + 前端 Nginx）
docker compose -f docker-compose.yml -f docker-compose.services.yml up -d

# 3. 停止
docker compose -f docker-compose.yml -f docker-compose.services.yml down
```

启动后等待约 60 秒，然后访问：

| 地址 | 说明 |
|------|------|
| http://localhost | 前端页面 |
| http://localhost:8080/actuator/health | 网关健康检查 |
| http://localhost:8848/nacos | Nacos 控制台 (nacos/nacos) |
| http://localhost:3000 | Grafana 监控 (admin/admin) |

---

## 二、混合模式（Docker 中间件 + 本地 Java）

适合开发调试，服务日志直接可见。

```bash
# 1. 启动中间件
docker compose up -d

# 2. 启动 6 个微服务（分别终端，或后台运行）
java -jar gateway-service/target/gateway-service-1.0.0-SNAPSHOT.jar
java -jar system-service/target/system-service-1.0.0-SNAPSHOT.jar
java -jar expense-service/target/expense-service-1.0.0-SNAPSHOT.jar
java -jar approval-service/target/approval-service-1.0.0-SNAPSHOT.jar
java -jar ai-service/target/ai-service-1.0.0-SNAPSHOT.jar
java -jar notification-service/target/notification-service-1.0.0-SNAPSHOT.jar

# 3. 启动前端
cd expense-web
npm install    # 仅首次
npm run dev

# 4. 访问 http://localhost:5173
```

---

## 三、Windows 一键脚本

双击项目根目录 `start-all.bat`，自动完成：编译、启动中间件、启动 6 个微服务、启动前端。

> 首次使用需先在 `expense-web/` 目录下执行 `npm install`。

---

## 默认账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | 超级管理员 |
| manager | admin123 | 审批人（经理） |
| director | admin123 | 审批人（总监） |
| finance | admin123 | 财务审核员 |
| cashier | admin123 | 出纳 |

---

## 接入真实 API（可选）

### 钉钉机器人通知

需设置 Windows 系统环境变量（已预设好则跳过）：

```bash
setx DINGTALK_WEBHOOK_URL "https://oapi.dingtalk.com/robot/send?access_token=你的token"
setx DINGTALK_SECRET "你的加签密钥"    # 开启了加签才需要
```

### DeepSeek AI 审单

```bash
setx DEEPSEEK_API_KEY "sk-你的key"
```

### 阿里云 OCR

```bash
setx OCR_APP_CODE "你的AppCode"
setx OCR_APP_KEY "你的AppKey"
setx OCR_APP_SECRET "你的AppSecret"
```

> 所有 API 均支持自动降级 Mock——未设置环境变量时使用模拟数据，不影响功能演示。

---

## 端口规划

| 端口 | 服务 |
|:---:|------|
| 80 | 前端 Nginx（Docker 模式） |
| 5173 | 前端 Vite（本地开发） |
| 8080 | 网关 Gateway |
| 8081 | 系统服务 System |
| 8082 | 报销服务 Expense |
| 8083 | 审批服务 Approval |
| 8084 | AI 服务 |
| 8085 | 通知服务 Notification |
| 3307 | MySQL |
| 6379 | Redis |
| 5672 | RabbitMQ |
| 8848 | Nacos |
| 9090 | Prometheus |
| 3000 | Grafana |

---

## 验证服务是否正常

```bash
# 健康检查（全部返回 200 即为正常）
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
curl http://localhost:8085/actuator/health

# 登录测试
curl -X POST http://localhost:8080/system/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"password\":\"admin123\"}"
```

---

## 常见问题

**Q: 端口被占用？**
```bash
# 查看端口占用
netstat -ano | findstr "8080"
# 终止进程
taskkill //F //PID 进程号
```

**Q: Docker 容器名称冲突？**
```bash
docker compose -f docker-compose.yml -f docker-compose.services.yml down
```

**Q: 前端页面空白？**
确认所有 6 个微服务健康检查都是 200，然后刷新页面。

**Q: 钉钉收不到通知？**
检查 `DINGTALK_WEBHOOK_URL` 环境变量是否设置（`echo %DINGTALK_WEBHOOK_URL%`），重新启动 notification-service 后生效。
