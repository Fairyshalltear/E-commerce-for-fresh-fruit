# Spec Delta

## Purpose

让已登录买家暂存要购买的水果 SKU、调整数量并选择结算项，同时避免购物车中的旧价格或库存状态被误当作下单保证。

## ADDED Requirements

### Requirement: Buyer adds and updates cart items
系统 SHALL 允许已登录买家将已上架且可售的 SKU 加入购物车、修改购买份数和删除条目。数量 SHALL 为正整数；同一买家的相同 SKU SHALL 合并为一个购物车条目。加入或修改购物车 SHALL 不预留库存。

#### Scenario: Buyer adds the same SKU again
- **WHEN** 买家再次将已在购物车中的同一 SKU 加入购物车
- **THEN** 系统增加该条目的购买份数而不创建重复条目

#### Scenario: Buyer requests unavailable quantity
- **WHEN** 买家加入或修改一个 SKU 的数量且该 SKU 已下架或当前可售库存不足
- **THEN** 系统拒绝该变更并返回可识别的库存或商品状态提示

### Requirement: Buyer selects cart items for checkout
系统 SHALL 允许买家选择、取消选择或全选购物车条目，并基于当前 SKU 价格计算已选条目的商品金额。运费 SHALL 为零。

#### Scenario: Cart subtotal reflects selected items
- **WHEN** 买家改变购物车条目的选择状态或购买份数
- **THEN** 系统仅按已选条目和当前 SKU 单价更新商品金额

### Requirement: Checkout revalidates cart items
系统 SHALL 在进入结算时重新校验已选 SKU 的上架状态、当前价格和可售库存；若任一条目不可购买，系统 SHALL 阻止提交并指出需要处理的条目。

#### Scenario: Cart state changed before checkout
- **WHEN** 买家结算的 SKU 已下架、价格已变化或可售库存已不足
- **THEN** 系统不创建订单并向买家返回最新商品状态，且不预留任何库存
