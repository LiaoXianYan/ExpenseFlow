@echo off
chcp 65001 > nul
echo ============================================
echo   ExpenseFlow 差旅报销智能管理平台
echo   一键启动脚本
echo ============================================

echo.
echo [1/4] 启动中间件 (MySQL/Redis/RabbitMQ/Nacos)...
docker compose -f docker-compose.yml up -d
echo 等待中间件就绪...
timeout /t 30 /nobreak > nul

echo.
echo [2/4] 编译后端服务...
call mvn package -DskipTests -q
if %errorlevel% neq 0 (
    echo 编译失败！请检查错误信息。
    pause
    exit /b 1
)
echo 编译完成

echo.
echo [3/4] 启动微服务...
for %%s in (gateway-service system-service expense-service approval-service ai-service notification-service) do (
    echo   启动 %%s...
    start "%%s" java -jar %%s\target\%%s-1.0.0-SNAPSHOT.jar
)
echo 等待服务启动 (60秒)...
timeout /t 60 /nobreak > nul

echo.
echo [4/4] 启动前端...
cd expense-web
start "expense-web" cmd /c "npm run dev"
cd ..

echo.
echo ============================================
echo   全部服务启动完成！
echo   前端: http://localhost:5173
echo   网关: http://localhost:8080
echo   Nacos: http://localhost:8848/nacos
echo   Grafana: http://localhost:3000
echo ============================================
pause
