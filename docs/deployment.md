# ExpenseFlow 部署指南

## 环境要求

| 软件 | 版本 | 说明 |
|------|------|------|
| JDK | 17+ | 后端编译与运行 |
| Maven | 3.6+ | 项目构建 |
| Node.js | 20+ | 前端构建 |
| Docker | 24+ | 中间件编排 |
| Docker Compose | v2 | 服务编排 |

## 快速启动 (Docker)

```bash
# 1. 克隆项目
git clone <repo-url> && cd ExpenseFlow

# 2. 编译后端
mvn package -DskipTests

# 3. 启动全部服务
docker compose -f docker-compose.yml -f docker-compose.services.yml up -d

# 4. 验证
curl http://localhost/actuator/health    # 前端 Nginx
curl http://localhost:8080/actuator/health  # 网关

# 5. 访问
# 前端: http://localhost
# Nacos: http://localhost:8848/nacos (nacos/nacos)
# Grafana: http://localhost:3000 (admin/admin)

# 6. 停止
docker compose -f docker-compose.yml -f docker-compose.services.yml down
```

## 本地开发启动

```bash
# 1. 启动中间件
docker compose up -d

# 2. 编译后端
mvn package -DskipTests

# 3. 启动各服务 (分别终端)
java -jar system-service/target/system-service-1.0.0-SNAPSHOT.jar
java -jar expense-service/target/expense-service-1.0.0-SNAPSHOT.jar
java -jar approval-service/target/approval-service-1.0.0-SNAPSHOT.jar
java -jar ai-service/target/ai-service-1.0.0-SNAPSHOT.jar
java -jar notification-service/target/notification-service-1.0.0-SNAPSHOT.jar

# 4. 启动前端
cd expense-web && npm install && npm run dev

# 5. 访问
# 前端: http://localhost:5173
```

## Windows 一键启动

双击 `start-all.bat`，自动完成中间件启动、后端编译、服务启动、前端启动。

## 接入真实 API

### DeepSeek AI 审单
```bash
# Windows
set DEEPSEEK_API_KEY=sk-xxxxxxxx
# Linux / Docker
export DEEPSEEK_API_KEY=sk-xxxxxxxx
```
设置后 AI 审单和 RAG 问答自动调用 DeepSeek Chat API。
未设置时自动降级为 Java Mock 引擎（与真实 API 结果逻辑一致）。

### 阿里云 OCR
在 `expense-service/src/main/resources/application.yml` 中修改：
```yaml
expense:
  ocr:
    mock: false
    access-key-id: your_aliyun_ak
    access-key-secret: your_aliyun_sk
```
或通过环境变量：
```bash
export ALIYUN_ACCESS_KEY_ID=your_aliyun_ak
export ALIYUN_ACCESS_KEY_SECRET=your_aliyun_sk
```
未设置时自动降级为 Mock OCR（识别模拟发票数据）。

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| DB_PASSWORD | root | MySQL 密码 |
| DEEPSEEK_API_KEY | sk-default | DeepSeek API Key (未设置自动 Mock) |
| ALIYUN_ACCESS_KEY_ID | - | 阿里云 AK (未设置自动 Mock) |
| ALIYUN_ACCESS_KEY_SECRET | - | 阿里云 SK (未设置自动 Mock) |

## 端口规划

| 端口 | 服务 |
|:---:|------|
| 80 | 前端 Nginx (生产) |
| 5173 | Vite Dev Server (开发) |
| 8080 | Gateway |
| 8081 | System |
| 8082 | Expense |
| 8083 | Approval |
| 8084 | AI |
| 8085 | Notification |
| 3307 | MySQL |
| 6379 | Redis |
| 5672 | RabbitMQ |
| 8848 | Nacos |
| 9090 | Prometheus |
| 3000 | Grafana |
