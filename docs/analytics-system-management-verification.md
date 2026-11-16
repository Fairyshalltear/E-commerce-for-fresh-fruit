# 数据统计与系统管理验证记录

## 已执行

- `cd back-end && mvn test -q`：通过。Surefire 汇总 81 项测试，失败 0、错误 0；其中 80 项执行，MySQL 迁移测试因缺少隔离数据库配置跳过 1 项。
- `cd back-end && mvn -q -Dtest=AuthenticationIntegrationTest test`：通过，8 项。覆盖店主初始化、店员登录、权限撤销、停用、凭据重置、未授权管理端点、未分类端点拒绝及审计。
- `cd back-end && mvn -q -Dtest=CatalogIntegrationTest test`：通过，26 项。包括商品上架敏感权限拒绝/允许、库存调整敏感权限拒绝/允许和拒绝审计。
- `cd back-end && mvn -q -Dtest=OrderIntegrationTest#supportsPartialUnshippedRefundAndReturnRestockWorkflow test`：通过。覆盖退款执行的独立授权与拒绝审计。
- `cd back-end && mvn -q -Dtest=AdminAnalyticsIntegrationTest test`：通过。覆盖上海日期范围、周一分组、空区间、失败支付排除、成功退款、全额退款后已支付销量、活跃买家去重、多商品并列排行、已删除商品排除及库存并列。
- `cd front-end && npm run build`：通过，包含 `vue-tsc -b` 与 Vite 生产构建。ECharts 统计页面 chunk 约 561 KB，Vite 发出超过 500 KB 的分块提示。
- 本地 Vite 开发服务器已启动：`http://127.0.0.1:5173/`。用浏览器检查了商城首页当前渲染状态，桌面截图未见首屏横向溢出；该截图不是 390px 尺寸检查。
- 本轮再次运行 `cd back-end && mvn test -q`：Surefire 报告 80 项执行、0 失败、0 错误，MySQL 迁移测试跳过 1 项；`cd front-end && npm run build` 通过（含 `vue-tsc -b`）。
- 使用临时本地 mock API（独立端口，不连接 `8080` 后端）在 1280px 浏览器检查统计、内容、店员和审计页面。统计 KPI、趋势图、订单状态图均渲染；店员权限列表、日志筛选/分页/详情可见，详情只呈现安全摘要字段。验证时发现统计图表在加载状态切换前初始化导致容器未挂载，已调整为加载完成后再绘制，并确认两张图表均创建 ECharts canvas 实例。
- 使用隔离 Chrome DevTools 会话分别设置 1280px 和 390px 视口，检查统计、内容、店员、审计及商城首页。各页面空态和管理 API 403 错误态均未造成整体横向溢出；统计页在两种视口均渲染 2 张图表。公告保存返回 403 时，已输入标题/正文保留在表单中。额外注入 240 字符轮播标题、240 字符店员账号和超长日志路径：初次发现前两项在桌面撑宽页面，已增加强制换行约束；修复后桌面与手机文档宽度分别保持 1280px / 390px，日志长路径正常换行。
- 仅店主路由限制及店员无法访问店员/审计 API 的自动化覆盖见 `AuthenticationIntegrationTest`；店员生命周期、模块授权拒绝/通过、拒绝日志覆盖见 `AuthenticationIntegrationTest` 与 `CatalogIntegrationTest`。
- `CATALOG_MIGRATION_DB_URL='jdbc:mysql://127.0.0.1:33307/freshfruit_migration?useSSL=false&allowPublicKeyRetrieval=true' CATALOG_TEST_DB_USERNAME=root CATALOG_TEST_DB_PASSWORD= mvn -q -Dtest=MySqlMigrationTest test`：在本轮创建的临时 MySQL 8.0 隔离数据目录中通过。验证从 V2 建库，经 V5 插入旧版管理员、订单、退款和库存流水数据后升级至 V7；二次 migrate 为 0，Flyway validate 成功，管理员 ID/凭据字段、库存数值/版本、历史日志、订单/售后行及库存流水关联均通过断言。临时实例位于 `/tmp/fresh-fruit-mysql.IGcvA9`，使用 33307 端口，不连接现有 3306 服务。
- 本轮 `cd back-end && mvn test -q`：通过。Surefire 汇总 80 项执行、0 失败、0 错误；独立 MySQL 验收由上条单独启用，故全量 H2 测试汇总中迁移测试跳过 1 项。

## 尚未完成的环境验收

- MySQL 迁移测试已在临时隔离实例通过；未连接当前运行在 3306 端口的已有 MySQL 服务。
- 尚未使用隔离测试后端账号做真实浏览器集成；模拟 owner/mock API 下已完成管理页桌面检查，但空态、错误态及页面级权限拒绝视觉状态仍未逐项验收。
- 本轮后台页面视觉检查使用模拟 owner 会话及 mock API，不代表完整真实服务端浏览器 E2E；权限拒绝由真实后端集成测试覆盖，前端 403 状态由 mock API 检查。桌面及 390px 布局、空态/错误态、长文本和横向溢出已检查。
- 生产构建的 ECharts chunk 大于 Vite 默认 500 KB 警告阈值；构建成功，但仍需评估是否拆分图表依赖。
