# 鲜果集：生鲜水果电商平台

毕业答辩版生鲜水果电商平台。当前已实现买家及管理员鉴权、个人资料和地址、商品推荐与搜索、规格详情、收藏，以及后台商品、分类和库存维护。购物车和订单等模块按 OpenSpec 任务继续开发。

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

服务默认监听 `http://localhost:8080`。启动时 Flyway 会自动执行 `back-end/src/main/resources/db/migration/` 中尚未应用的迁移：V1 创建账户，V2 创建地址，V3 创建商品、分类、SKU、图片、库存、流水、收藏、只读评价和管理记录。可用 `flyway_schema_history` 检查迁移状态。

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

## 商品浏览与商品管理

商城首页提供商品推荐；`/products` 支持关键词、两级分类、价格区间、排序和分页，`/products/:productId` 展示图片、规格、独立价格、可售库存、商品介绍和评价。买家可收藏，并在 `/account/favorites` 查看或取消收藏。

管理员登录后进入 `/admin/products`，在 `/admin/categories` 建立两级分类。商品编辑支持多图片、多 SKU、推荐标记和顺序。新商品默认下架，保存后可上架；商品只归属二级分类。售罄仍展示，下架/删除或分类不可用的收藏显示失效提示。

V3 自动创建商品相关表，保留现有账户及地址；不用手工建表。新库暂无商品，使用管理界面显式准备演示商品和图片。评价本阶段只展示，提交评价随订单完成校验实现。图片目录由 `PRODUCT_IMAGE_STORAGE_DIR` 配置，默认 `./uploads/products`，与头像分开；数据库备份须包含图片目录。

详细接口、库存规则、图片清理和验收步骤见 [商品 API 与验收](docs/catalog-api.md)。更新代码后重新启动后端，加载新接口及 Flyway 迁移；前端开发服务热更新。不要改写已应用 V1/V2。

`.env` 中带 `&`、`?` 或空格的值应使用引号包裹，再按上文 source 导出，特别是 JDBC URL；密钥和密码仅保留在本机。

2026-11-13 商品模块验证：完整后端测试 40 项通过，另 1 项 MySQL 专用迁移测试在默认 H2 运行中跳过；在两个隔离 MySQL 8.0.46 空库执行 24 项商品集成测试及 1 项迁移测试，全部通过。前端 typecheck/build、管理到商城收藏闭环、390 像素手机与 1280 像素桌面布局检查通过。详见 [商品验收记录](docs/catalog-verification.md)。
