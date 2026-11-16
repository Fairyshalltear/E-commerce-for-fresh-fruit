# 管理端经营统计 API

`GET /api/v1/admin/analytics` 返回当前统计汇总、趋势、订单状态分布、热销商品和可售库存排行。需要店主身份或店员 `STATISTICS` 模块权限。查询不返回买家登录明细。

| 参数 | 说明 |
| --- | --- |
| `period` | `TODAY`、`WEEK`、`MONTH`、`LAST_30_DAYS` 或 `CUSTOM`，默认 `MONTH` |
| `from`、`to` | `CUSTOM` 时必填，格式 `yyyy-MM-dd`；首尾日期均包含 |
| `grain` | `DAY`、`WEEK` 或 `MONTH`，默认 `DAY`；周以周一为起始 |

所有日历边界均按 `Asia/Shanghai` 计算。自定义区间最多覆盖三年，且不能查询未来日期。趋势会为无数据的时间桶补零。

销售额是区间内成功支付金额减成功退款金额；退款按成功退款事件时间归属。销量是已支付商品数量减成功退款商品数量，失败或待付款订单不计入。订单数按创建时间统计，状态分布是该区间创建订单的当前状态。注册量按账号创建时间统计。活跃买家是区间内成功登录买家与下单买家的去重并集，订单不要求支付成功。

热销排行按统计区间净销量降序、商品 ID 升序排列，展示净销售额；库存排行按启用 SKU 的 `on_hand - reserved` 汇总可售库存降序排列。两榜排除逻辑删除商品，最多返回 10 项。

## 验证

```bash
mvn -f back-end/pom.xml -Dtest=AdminAnalyticsIntegrationTest test
```
