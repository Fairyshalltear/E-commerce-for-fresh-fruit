# Proposal

## Why

项目需要将毕业设计开题材料中的生鲜水果电商业务转化为可运行、可演示的前后端系统。当前工作区只有前后端空目录，尚无实现；需要先约定角色、交易与库存规则、答辩版外部服务边界及技术方案，避免实现过程中产生互相矛盾的业务行为。

## What Changes

- 新增面向消费者的水果浏览、账号、购物车、下单、模拟支付、订单履约、评价与售后能力。
- 新增面向管理员的商品与 SKU、库存、订单、用户、站点内容、统计及 AI 知识管理能力。
- 将库存按 SKU 汇总管理；下单预留库存，取消或支付超时释放，发货时扣减实物库存；不建设批次和临期追踪。
- 将支付、物流与 AI 控制在可离线准备的答辩演示范围：不连接真实支付或快递平台；AI 使用本地 embedding 检索和模板回答，不调用在线大模型。
- 采用 Vue 3 与 TypeScript 前端、Java 17 与 Spring Boot 后端、MySQL 8 数据库；建立按功能模块提交并推送 GitHub 的版本管理约定。

## Capabilities

### New Capabilities
- `user-accounts`: 买家注册登录、资料和地址管理，以及管理员对买家账户和管理员账户的管理。
- `fruit-catalog`: 水果分类、商品与 SKU 管理、商品浏览搜索、收藏及商品信息展示。
- `shopping-cart`: 买家购物车维护与结算商品选择。
- `order-fulfillment`: 结算、模拟支付、订单状态、库存预留衔接、模拟发货与物流轨迹、确认收货。
- `inventory-control`: SKU 汇总库存、出入库、盘点、库存流水和低库存预警。
- `after-sales`: 退款与退货申请、管理员审核、模拟退款及退货入库。
- `product-reviews`: 已完成订单商品的评分与文字评价。
- `ai-assistance`: 本地语义知识检索、模板应答、多轮会话、历史记录及知识库管理。
- `storefront-content`: 首页轮播图与公告管理和展示。
- `sales-analytics`: 销售、订单、用户及商品基础统计。
- `platform-administration`: 管理员操作记录、系统日志及答辩演示所需的后台治理能力。

### Modified Capabilities

无。项目当前没有既有能力规格。

## Impact

- 新增 `front-end/` 下的 Vue 商城与管理端路由及界面，以及 `back-end/` 下的 Spring Boot REST API。
- 新增 MySQL 数据模型与迁移脚本；预计围绕账号、商品/SKU、购物车、订单/支付/物流、库存、售后、评价、AI 知识与会话、站点内容和审计统计建立约 25-30 张核心表，最终以设计阶段的规范化模型为准。
- 引入 Spring Security/JWT、MyBatis-Plus、Spring AI ONNX embedding 等后端依赖，以及 Element Plus 等前端依赖；不引入 Redis 或独立向量数据库。
- GitHub 远程目标为 `https://github.com/Fairyshalltear/E-commerce-for-fresh-fruit.git`。完成并验证一个功能模块后形成独立提交并推送；模型权重、凭据和本地配置不进入版本库。
- 不包含真实支付、第三方物流查询、短信服务、在线大模型调用、库存批次/临期追踪及复杂配送计费。
