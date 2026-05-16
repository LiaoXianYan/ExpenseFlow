# ExpenseFlow 启动指南

> 适用于开发、演示、部署三种场景。请根据用途选择启动方式。

## 前置要求

| 软件 | 最低版本 | 用途 |
|---|---|---|
| JDK | 17+ | 编译和运行 Java 服务 |
| Maven | 3.9+ | 构建项目 |
| Docker & Docker Compose | 24+ | 容器化运行中间件和微服务 |
| Node.js | 18+ | 前端开发（方式 2/3 需要） |
| npm | 9+ | 前端依赖和开发服务器 |

---

## 一、全 Docker 启动（推荐：演示/部署）

所有服务以容器方式运行，一次启动，不需要安装 JDK/Maven/Node.js。

### 1.1 首次启动

```bash
# 1. 打包所有服务（在项目根目录）
mvn clean package -DskipTests

# 2. 启动中间件（MySQL / Redis / RabbitMQ / Nacos / Prometheus / Grafana）
docker compose -f docker-compose.yml up -d

# 3. 等待 MySQL 健康就绪（约 30 秒）
#    可执行以下命令检查：
docker compose -f docker-compose.yml ps

# 4. 启动微服务 + 前端
docker compose -f docker-compose.yml -f docker-compose.services.yml up -d --build

# 5. 等待全部服务就绪（约 60 秒），然后验证
curl http://localhost:8080/actuator/health   # 网关
curl http://localhost:8081/actuator/health   # 系统服务
curl http://localhost:8082/actuator/health   # 报销服务
curl http://localhost:8083/actuator/health   # 审批服务
curl http://localhost:8084/actuator/health   # AI 服务
curl http://localhost:8085/actuator/health   # 通知服务
```

每个应返回 `{"status":"UP"}`。

### 1.2 日常启动/停止

```bash
# 启动（不重新构建）
docker compose -f docker-compose.yml -f docker-compose.services.yml up -d

# 停止微服务（保留中间件数据）
docker compose -f docker-compose.yml -f docker-compose.services.yml stop gateway-service system-service expense-service approval-service ai-service notification-service frontend

# 全部停止
docker compose -f docker-compose.yml -f docker-compose.services.yml down

# 全部停止 + 清空数据（危险！）
docker compose -f docker-compose.yml -f docker-compose.services.yml down -v
```

### 1.3 代码变更后更新

```bash
# 1. 重新打包
mvn clean package -DskipTests

# 2. 重建并重启变更的服务（例如只改过 expense-service）
docker compose -f docker-compose.yml -f docker-compose.services.yml up -d --build expense-service
```

### 1.4 访问地址

| 项目 | 地址 |
|---|---|
| 前端页面 | http://localhost |
| 网关 | http://localhost:8080 |
| Nacos 控制台 | http://localhost:8848/nacos（用户名/密码 nacos/nacos） |
| RabbitMQ 管理台 | http://localhost:15672（用户名/密码 guest/guest） |
| Grafana | http://localhost:3000（用户名/密码 admin/admin） |

---

## 二、半自动启动（演示/快速体验）

中间件用 Docker，微服务和前端直接在本机跑。适合不用 Docker 运行 Java 的场景。

### 2.1 一键脚本（Windows）

项目根目录提供了 `start-all.bat`：

```
双击 start-all.bat
```

脚本会自动：
1. 启动 Docker 中间件（MySQL/Redis/RabbitMQ/Nacos）
2. `mvn package` 编译所有服务
3. 打开 6 个命令行窗口，各自运行 `java -jar xxx.jar`
4. 打开第 7 个窗口运行 `npm run dev`
5. 等待 60 秒后浏览器访问 http://localhost:5173

### 2.2 手动执行（macOS / Linux）

```bash
# 1. 启动中间件
docker compose -f docker-compose.yml up -d

# 2. 等待 MySQL 就绪（约 30 秒）
# 3. 编译
mvn clean package -DskipTests

# 4. 启动微服务（6 个终端窗口，顺序启动）
java -jar gateway-service/target/gateway-service-1.0.0-SNAPSHOT.jar &
java -jar system-service/target/system-service-1.0.0-SNAPSHOT.jar &
java -jar expense-service/target/expense-service-1.0.0-SNAPSHOT.jar &
java -jar approval-service/target/approval-service-1.0.0-SNAPSHOT.jar &
java -jar ai-service/target/ai-service-1.0.0-SNAPSHOT.jar &
java -jar notification-service/target/notification-service-1.0.0-SNAPSHOT.jar &

# 5. 启动前端
cd expense-web && npm install && npm run dev
```

访问 http://localhost:5173

---

## 三、纯手动启动（开发调试）

中间件在 Docker，所有后端服务通过 Maven 插件启动，前端通过 Vite 热更新。

### 3.1 启动中间件

```bash
docker compose -f docker-compose.yml up -d mysql redis rabbitmq nacos
```

### 3.2 初始化数据库（仅首次）

MySQL 启动后自动执行 `sql/init.sql`。如果容器已存在且需要重新初始化：

```bash
docker compose -f docker-compose.yml down -v mysql
docker compose -f docker-compose.yml up -d mysql
```

### 3.3 启动后端（6 个终端，逐个启动）

在每个终端中，切换到项目根目录后执行：

**终端 1 — 系统服务（8081）**
```bash
mvn spring-boot:run -pl system-service
```

**终端 2 — 报销服务（8082）**
```bash
mvn spring-boot:run -pl expense-service
```

**终端 3 — 审批服务（8083）**
```bash
mvn spring-boot:run -pl approval-service
```

**终端 4 — AI 服务（8084）**
```bash
mvn spring-boot:run -pl ai-service
```

**终端 5 — 通知服务（8085）**
```bash
mvn spring-boot:run -pl notification-service
```

**终端 6 — 网关（8080）**
```bash
mvn spring-boot:run -pl gateway-service
```

### 3.4 启动前端

```bash
cd expense-web
npm install        # 仅首次
npm run dev
```

### 3.5 验证

```bash
# 所有服务 health check
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8081/actuator/health
curl -s http://localhost:8082/actuator/health
curl -s http://localhost:8083/actuator/health
curl -s http://localhost:8084/actuator/health
curl -s http://localhost:8085/actuator/health

# 前端
curl -s http://localhost:5173 | head -5
```

全部返回 `{"status":"UP"}` 即启动成功。浏览器打开 http://localhost:5173

---

## 账号密码

| 用户名 | 密码 | 角色 | 可访问功能 |
|---|---|---|---|
| admin | admin123 | 超级管理员 | 全部功能 |
| manager | admin123 | 经理（审批人） | 审批工作台 |
| director | admin123 | 总监（审批人） | 审批工作台 |
| finance | admin123 | 财务审核 | 审批工作台、AI 审单 |
| cashier | admin123 | 出纳 | 打款 |

---

## 端口映射

| 端口 | 服务 | 说明 |
|---|---|---|
| 80 | 前端（Nginx） | 生产模式前端访问入口 |
| 8080 | Gateway | API 网关 |
| 8081 | system-service | 用户/角色/权限/认证 |
| 8082 | expense-service | 出差申请/报销单/发票/打款 |
| 8083 | approval-service | 审批工作流/规则引擎 |
| 8084 | ai-service | OCR 识别/AI 审单/RAG 问答 |
| 8085 | notification-service | 站内消息/钉钉推送 |
| 8848 | Nacos | 注册中心 + 配置中心 |
| 3307 | MySQL | 数据库（宿主端口 3307 → 容器 3306） |
| 5672 | RabbitMQ | 消息队列 |
| 6379 | Redis | 缓存 + 黑名单 |
| 9090 | Prometheus | 监控指标收集 |
| 3000 | Grafana | 监控面板 |

---

## 常见问题

### Q: 启动后页面空白或 API 返回 502

前端 Nginx 需要转发 `Authorization` 和 `X-Tenant-Id` 请求头。Docker 启动时使用项目根目录的 `docker/nginx.conf` 或 `expense-web/nginx.conf`，确保包含：

```nginx
proxy_set_header Authorization $http_authorization;
proxy_set_header X-Tenant-Id $http_x_tenant_id;
```

### Q: MySQL 端口冲突

项目使用宿主机 3307 端口映射到容器 3306。如果本地已有 MySQL 占用 3307，修改 `docker-compose.yml` 中 MySQL 的 `ports` 配置。

### Q: Nacos 控制台显示服务为空

Docker 模式下，服务注册 IP 为容器内网 IP（172.x.x.x），网关通过容器网络通信。本地手动模式下，注册 IP 为本机 IP（192.168.x.x）。两种模式**不能混用**。

### Q: 前端 dev server 登录成功但页面数据为空

Vite dev server 通过自身代理（`vite.config.ts` 中配置）转发 API 到 `localhost:8081-8085`。确保对应端口的微服务已启动。

### Q: `mvn spring-boot:run` 报 Nacos 连接错误

检查 Docker 中间件是否已启动：
```bash
docker compose -f docker-compose.yml ps
```
确保 nacos 状态为 `healthy`。

### Q: 如何清空所有数据重新开始

```bash
docker compose -f docker-compose.yml -f docker-compose.services.yml down -v
docker compose -f docker-compose.yml up -d
```

### Q: 运行测试

```bash
mvn test          # 所有服务全部测试（96 个）
mvn test -pl expense-service    # 单个服务测试
cd expense-web && npx vitest run  # 前端测试
```
