# 管理员买家管理 API

以下接口仅接受 ADMIN Bearer token，并使用统一 `ApiResponse`。列表及详情仅返回买家基本资料、状态和注册时间，不包含密码哈希、地址或订单。

| 方法 | 路径 | 请求/用途 |
| --- | --- | --- |
| `GET` | `/api/v1/admin/buyers` | 分页搜索；可选 `keyword`（用户名/手机号/昵称）、`status`（0/1）、`page`（默认 1）、`size`（默认 12，最大 100） |
| `GET` | `/api/v1/admin/buyers/{buyerId}` | 读取买家基本资料、账户状态和注册时间 |
| `PATCH` | `/api/v1/admin/buyers/{buyerId}/status` | 启用/禁用：`{"status": 1}` 或 `{"status": 0}` |
| `POST` | `/api/v1/admin/buyers/{buyerId}/password-reset` | 设置临时密码：`{"temporaryPassword":"TemporaryPassword123!"}`，长度 8-72 字节 |

状态无变化时不写重复审计记录。状态变化及密码重置都递增 `credential_version`，使目标买家已签发的 JWT 立即失效；重置密码不自动发放令牌。成功操作与审计事件在同一事务提交，审计不记录临时密码或其哈希。

## 验证

```bash
mvn -f back-end/pom.xml -Dtest=BuyerAdministrationIntegrationTest test
```

测试覆盖管理员授权、字段白名单、旧令牌撤销、BCrypt 重置、审计及密码策略拒绝。
