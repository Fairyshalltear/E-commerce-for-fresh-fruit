# 店员与权限管理 API

仅店主可调用 `/api/v1/admin/staff`。店员无法读取或变更他人账号权限。账号不硬删除；停用、启用、凭据重置和权限集合替换均记录审计并递增凭据版本，使已有 token 立即失效。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `GET` | `/api/v1/admin/staff` | 查询店员和授权集合 |
| `POST` | `/api/v1/admin/staff` | 创建店员；权限初始值由店主显式提交 |
| `PUT` | `/api/v1/admin/staff/{id}` | 更新显示名，并完整替换模块/敏感操作权限集合 |
| `PATCH` | `/api/v1/admin/staff/{id}/enabled` | 启用或停用账号 |
| `POST` | `/api/v1/admin/staff/{id}/credentials` | 重置店员密码 |

模块权限为 `PRODUCTS`、`BUYERS`、`ORDERS`、`INVENTORY`、`AFTER_SALES`、`CONTENT`、`STATISTICS`。敏感操作权限为 `REFUND_EXECUTE`、`PRODUCT_STATUS_CHANGE`、`INVENTORY_ADJUST`；执行时还要求对应模块权限。店员权限默认为空。权限不保存在 token 中，后端每次管理请求读取当前账号状态和权限。

## 验证

```bash
mvn -f back-end/pom.xml -Dtest=AuthenticationIntegrationTest test
```
