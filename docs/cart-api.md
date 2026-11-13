# 购物车 API

所有接口使用 `ApiResponse` 包装，买家接口需携带 `Authorization: Bearer <accessToken>`。用户身份始终从令牌读取，客户端不得提交 `buyerId`。金额以十进制字符串返回。

## 接口

| 方法 | 路径 | 请求/用途 |
| --- | --- | --- |
| `GET` | `/api/v1/buyer/cart` | 读取购物车及当前 SKU 价格、库存、可购买状态和已选合计 |
| `POST` | `/api/v1/buyer/cart` | 添加 SKU：`{"skuId": 12, "quantity": 2}`；相同 SKU 合并 |
| `PATCH` | `/api/v1/buyer/cart/items/{itemId}/quantity` | 修改数量：`{"quantity": 3}` |
| `PATCH` | `/api/v1/buyer/cart/items/{itemId}/selection` | 设置单条选择：`{"selected": true}` |
| `PUT` | `/api/v1/buyer/cart/selection` | 全选/全不选：`{"selected": false}` |
| `PUT` | `/api/v1/buyer/cart/items/{itemId}/accepted-price` | 确认新单价：`{"price": "12.00"}`；服务器会复核该价格仍为当前价 |
| `DELETE` | `/api/v1/buyer/cart/items/{itemId}` | 删除购物车条目 |
| `POST` | `/api/v1/buyer/cart/checkout-validation` | 校验已选商品；只校验，不创建订单或预留库存 |

购物车条目包含 `currentUnitPrice`、`acceptedUnitPrice`、`priceChanged`、`availableStock`、`purchasable`、`unavailableReason` 和 `lineAmount`。不可购买的条目会保留，买家仍可取消选择或删除。`selectedSubtotal` 和 `total` 只合计已选且可购买条目；`shippingFee` 为 `0.00`。

结算校验返回 `valid`、最新购物车、`attentionItemIds` 及提示。商品不可售、库存不足或价格不同于买家已接受价格时，`valid` 为 `false`。价格变更后调用确认单价接口，再重新校验。购物车中的库存不是预留量，提交订单（本模块之外）时仍须重新检查。

## 常见错误

| HTTP | `code` | 含义 |
| --- | --- | --- |
| `401` | `UNAUTHORIZED` | 未登录、买家已停用或令牌已撤销 |
| `403` | `FORBIDDEN` | 管理员或其他非买家角色访问买家购物车 |
| `404` | `NOT_FOUND` | 条目不存在或不属于当前买家 |
| `409` | `INSUFFICIENT_STOCK` | 加购或改数量超过当前可售库存 |
| `409` | `ITEM_UNAVAILABLE` | 商品、SKU 或分类当前不可售 |
| `409` | `PRICE_CHANGED` | 确认价格时商品已再次改价 |
| `400` | `CART_SELECTION_REQUIRED` | 未选择任何商品进行校验 |

## 验证

```bash
mvn -f back-end/pom.xml -Dtest=CartIntegrationTest test
```

集成测试验证重复 SKU 合并、当前价合计、超库存拒绝、价格确认、失效条目、角色/所有权边界及购物车操作不改变库存预留量。
