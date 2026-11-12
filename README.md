# 鲜果集：生鲜水果电商平台

毕业答辩版生鲜水果电商平台。当前基础模块包括 Vue 3 前端骨架、Spring Boot REST API、MySQL/Flyway 数据库迁移、统一接口响应、买家注册登录和管理员登录鉴权。商品、购物车、订单等业务模块按 OpenSpec 任务逐步开发。

## 技术栈

- 后端：Java 17、Spring Boot 3.5、Spring Security、JWT、MyBatis-Plus、MySQL 8、Flyway
- 前端：Vue 3、TypeScript、Vite、Vue Router、Pinia、Axios
- 测试：Spring Boot Test、MockMvc、测试专用 H2 数据库

## 环境准备

- JDK 17 或更高版本
- Node.js 20 或更高版本及 npm
- MySQL 8，或 Docker Desktop 与 Docker Compose
- Maven 3.9+（也可以使用本机安装的 Maven）

复制环境变量模板，并设置只在本机使用的密码和密钥：

```bash
cp .env.example .env
openssl rand -base64 32
```

将生成的 Base64 字符串填入 `.env` 的 `JWT_SECRET`，并设置 `MYSQL_ROOT_PASSWORD`、`DB_PASSWORD` 和至少 12 位的 `ADMIN_INITIAL_PASSWORD`。这些值不能提交到 Git。Spring Boot 不会自动读取 `.env`，启动后端时需按下方命令导出变量。

## 启动 MySQL

使用 Docker Compose：

```bash
docker compose up -d mysql
docker compose ps
```

Compose 会从根目录 `.env` 读取数据库名、端口、用户名和密码。若使用本机 MySQL，先创建数据库和应用用户，再确保 `.env` 中的 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 与本机配置一致：

```sql
CREATE DATABASE fresh_fruit CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'freshfruit'@'localhost' IDENTIFIED BY '替换为本地数据库密码';
GRANT ALL PRIVILEGES ON fresh_fruit.* TO 'freshfruit'@'localhost';
```

## 启动后端

在项目根目录打开终端：

```bash
set -a
source .env
set +a
mvn -f back-end/pom.xml spring-boot:run
```

服务默认监听 `http://localhost:8080`。启动时 Flyway 会自动执行 `back-end/src/main/resources/db/migration/` 中尚未应用的迁移；当前 V1 创建 `buyer_account` 和 `admin_account`。可用 `flyway_schema_history` 检查迁移状态。

当管理员表为空时，应用使用 `ADMIN_INITIAL_USERNAME` 和 `ADMIN_INITIAL_PASSWORD` 创建首个管理员；管理员已存在时不会覆盖或重置账户。应用不会提供公开管理员注册接口。JWT 密钥必须是 Base64 编码且解码后至少 32 字节，否则应用拒绝启动。

## 启动前端

在另一个终端中运行：

```bash
cd front-end
npm ci
npm run dev
```

打开 Vite 输出的本地地址，默认是 `http://localhost:5173`。开发服务器会将 `/api` 请求代理到 `http://localhost:8080`；如需更改后端地址，可设置 `VITE_API_PROXY_TARGET`。前端提供买家注册/登录、管理员登录入口、令牌持久化、退出登录和买家/管理员路由角色保护。

## API 快速验证

健康检查无需登录：

```bash
curl http://localhost:8080/api/v1/health
```

注册买家（用户名 3 至 50 位、以英文字母开头；密码 8 至 72 位）：

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo_buyer","phone":"13800000000","password":"DemoBuyerPassword123!","nickname":"演示买家"}'
```

买家可以用用户名或手机号登录：

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"usernameOrPhone":"demo_buyer","password":"DemoBuyerPassword123!"}'
```

管理员使用 `.env` 中配置的初始账号登录：

```bash
curl -X POST http://localhost:8080/api/v1/admin/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"替换为 ADMIN_INITIAL_PASSWORD"}'
```

成功和错误响应统一为 `code`、`message`、`data` 三个字段。买家登录响应中的 `data.accessToken` 可用于受保护接口：

```bash
curl http://localhost:8080/api/v1/auth/buyer/me \
  -H 'Authorization: Bearer 替换为买家 accessToken'
```

## 测试

```bash
mvn -f back-end/pom.xml test
cd front-end
npm run build
```

后端集成测试使用内存 H2，不要求启动 MySQL；真实本地运行仍使用 MySQL 8 和 Flyway。

## 验证记录

2026-11-12 基础模块验证：

- `mvn -f back-end/pom.xml test`：4 项测试通过，覆盖统一校验错误响应、买家注册与登录、重复手机号、BCrypt 密码、管理员初始化幂等和角色访问控制。
- 在隔离 MySQL 8.0.46 数据库上连续启动后端两次：V1 首次成功应用，第二次提示 schema 已是最新；买家用户名/手机号及管理员用户名唯一索引存在。
- 按本 README 导出环境变量并启动后端：健康检查正常，管理员登录返回 HTTP 200。
- `npm run build`：TypeScript 检查与 Vite 生产构建通过。
