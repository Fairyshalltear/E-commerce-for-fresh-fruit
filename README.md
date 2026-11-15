# 鲜果集：生鲜水果电商平台

毕业答辩版生鲜水果电商平台。已实现买家及管理员鉴权、个人资料和地址、商品目录、收藏、购物车、订单履约、模拟支付、模拟物流、退款退货及评价治理。

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

服务默认监听 `http://localhost:8080`。启动时 Flyway 会自动执行 `back-end/src/main/resources/db/migration/` 中尚未应用的迁移：V1–V4 创建账户、地址、商品/库存/评价基础表及购物车，V5 创建订单、支付、发货物流、售后和评价删除审计表。可用 `flyway_schema_history` 检查迁移状态。

当管理员表为空时，应用使用 `ADMIN_INITIAL_USERNAME` 和 `ADMIN_INITIAL_PASSWORD` 创建首个管理员；管理员已存在时不会覆盖或重置账户。应用不会提供公开管理员注册接口。JWT 密钥必须是 Base64 编码且解码后至少 32 字节，否则应用拒绝启动。

## 启动前端

在另一个终端中运行：

```bash
cd front-end
npm ci
npm run dev
```

打开 Vite 输出的本地地址，默认是 `http://localhost:5173`。开发服务器会将 `/api` 请求代理到 `http://localhost:8080`；如需更改后端地址，可设置 `VITE_API_PROXY_TARGET`。买家可从 `/cart` 或商品详情立即购买进入共用结算页，在 `/account/orders` 查询和处理订单、申请售后、登记退货物流及提交已完成订单评价。管理员可在 `/admin/orders` 发货和登记模拟物流，在 `/admin/after-sales` 审核退款/退货并登记退款和入库数量，在 `/admin/reviews` 按原因删除违规评价；买家管理位于 `/admin/buyers`。账户身份始终以服务端 JWT 为准。

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

后端集成测试使用内存 H2，不要求启动 MySQL；真实本地运行仍使用 MySQL 8 和 Flyway。新库启动后 V4 创建 `cart_item` 并初始化买家 `credential_version`，V5 创建交易相关表。建议冒烟验收：创建商品和地址，加入购物车进入结算或商品页立即购买，提交订单后使用模拟支付、管理员发货、买家确认收货并评价；再验证售后申请和后台审核，以及公开评价列表的删除过滤。买家/管理员的越权请求应被拒绝。

购物车接口及错误响应见 [购物车 API](docs/cart-api.md)，订单、售后与评价接口见 [订单、售后与评价 API](docs/order-after-sales-reviews-api.md)，管理员买家管理接口见 [买家管理 API](docs/buyer-management-api.md)。

订单未付款超时默认 15 分钟，可通过 `ORDER_UNPAID_TIMEOUT_MINUTES` 调整。支付宝/微信付款、退款和物流轨迹均为本地模拟，不发起外部请求。真实支付或物流供应商接入需单独实现适配器、安全回调及凭据配置。本地 H2 测试 schema 与 V5 迁移同步维护。

要验证从旧版 schema 升级至 V5，请准备一个**专用、空白、可丢弃**的 MySQL 测试数据库（不要指向开发或生产业务库），然后设置迁移测试环境变量并运行测试。测试会先迁移到 V2 写入一条旧版本买家/地址数据，再升级到最新版本，检查数据保留、V4 字段、购物车和订单售后表，并重复迁移确认幂等：

```bash
export CATALOG_MIGRATION_DB_URL='jdbc:mysql://127.0.0.1:3306/fresh_fruit_migration_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
export CATALOG_TEST_DB_USERNAME='替换为测试数据库用户'
export CATALOG_TEST_DB_PASSWORD='替换为测试数据库密码'
mvn -f back-end/pom.xml -Dtest=MySqlMigrationTest test
```

成功后可在该专用库的 `flyway_schema_history` 确认 V1-V5 均已应用。测试库初始必须为空，测试会创建 schema 对象和固定 ID 的迁移验证数据。

历史验收记录见下文。订单模块最近一次验证结果见下方 2026-11-15 记录。

## 验证记录

2026-11-12 基础模块验证：

- `mvn -f back-end/pom.xml test`：4 项测试通过，覆盖统一校验错误响应、买家注册与登录、重复手机号、BCrypt 密码、管理员初始化幂等和角色访问控制。
- 在隔离 MySQL 8.0.46 数据库上连续启动后端两次：V1 首次成功应用，第二次提示 schema 已是最新；买家用户名/手机号及管理员用户名唯一索引存在。
- 按本 README 导出环境变量并启动后端：健康检查正常，管理员登录返回 HTTP 200。
- `npm run build`：TypeScript 检查与 Vite 生产构建通过。

## 商品浏览与商品管理

商城首页提供商品推荐；`/products` 支持关键词、两级分类、价格区间、排序和分页，`/products/:productId` 展示图片、规格、独立价格、可售库存、商品介绍和评价。买家可收藏，并在 `/account/favorites` 查看或取消收藏。

管理员登录后进入 `/admin/products`，在 `/admin/categories` 建立两级分类。商品编辑支持多图片、多 SKU、推荐标记和顺序。新商品默认下架，保存后可上架；商品只归属二级分类。售罄仍展示，下架/删除或分类不可用的收藏显示失效提示。

V3 自动创建商品相关表，保留现有账户及地址；不用手工建表。新库暂无商品，使用管理界面显式准备演示商品和图片。商品评价现与已完成订单明细关联，评价及删除审计由 V5 支持。图片目录由 `PRODUCT_IMAGE_STORAGE_DIR` 配置，默认 `./uploads/products`，与头像分开；数据库备份须包含图片目录。

详细接口、库存规则、图片清理和验收步骤见 [商品 API 与验收](docs/catalog-api.md)。更新代码后重新启动后端，加载新接口及 Flyway 迁移；前端开发服务热更新。不要改写已应用 V1/V2。

`.env` 中带 `&`、`?` 或空格的值应使用引号包裹，再按上文 source 导出，特别是 JDBC URL；密钥和密码仅保留在本机。

2026-11-13 商品模块验证：完整后端测试 40 项通过，另 1 项 MySQL 专用迁移测试在默认 H2 运行中跳过；在两个隔离 MySQL 8.0.46 空库执行 24 项商品集成测试及 1 项迁移测试，全部通过。前端 typecheck/build、管理到商城收藏闭环、390 像素手机与 1280 像素桌面布局检查通过。详见 [商品验收记录](docs/catalog-verification.md)。

2026-11-15 订单、售后与评价验证：后端完整测试 61 项、0 失败，默认跳过 1 项专用 MySQL 测试；订单生命周期专项测试 12 项通过。修正了订单筛选集成测试使用固定日期导致跨日失败的问题。隔离 `fresh_fruit_migration_test` 库的 MySQL V1–V5 升级、旧数据保留、重复迁移和 Flyway 校验已于 2026-11-14 验证通过。前端 `npm run typecheck`、`npm run build` 通过；认证买家订单/结算空态在 390px 与 1280px 视口无横向溢出，买家访问管理员路由会被重定向。当前隔离验收库无商品，完整下单/支付/售后/评价的浏览器交互仍需准备带有效商品图片的测试商品后执行；此前管理员订单、售后和评价页面的空态桌面/移动检查通过。
