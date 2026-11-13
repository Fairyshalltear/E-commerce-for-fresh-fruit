# Spec Delta

## Purpose

让已登录买家能够跨访问维护待购买的水果 SKU、选择结算商品并查看准确的当前金额，同时在订单创建前识别商品状态、价格或库存变化。本能力只负责购物车，不创建订单或预留库存。

## ADDED Requirements

### Requirement: Buyer maintains cart quantities
系统 SHALL 允许已登录买家将可售 SKU 加入购物车、修改正整数数量及删除条目；同一买家的同一 SKU SHALL 合并为一个条目，购物车变更 SHALL 不预留库存。

#### Scenario: Buyer adds a SKU already in the cart
- **WHEN** 买家再次添加同一可售 SKU
- **THEN** 系统增加原条目的数量且不创建重复条目

#### Scenario: Buyer requests more than available stock
- **WHEN** 买家添加或修改的数量大于该 SKU 当前可售库存
- **THEN** 系统拒绝该变更并返回可识别的库存不足提示

#### Scenario: Buyer changes another buyer's cart
- **WHEN** 买家尝试访问或修改其他买家的购物车条目
- **THEN** 系统拒绝请求且不泄露或更改该条目

### Requirement: Cart displays current product availability and price
系统 SHALL 在读取购物车时提供每个条目的当前商品/SKU 信息、当前单价、可售库存和可购买状态，并保留买家最近一次接受的单价基准；商品不可售时 SHALL 保留条目以供买家识别或删除。

#### Scenario: Cart item becomes unavailable
- **WHEN** 已加入的商品下架、SKU 停用或可售库存不足以满足条目数量
- **THEN** 购物车显示不可购买状态及当前可用信息，且买家仍可删除该条目

### Requirement: Buyer selects items and views subtotal
系统 SHALL 允许买家选择或取消选择单个条目及全选/全不选，并仅按已选且当前可购买条目的当前 SKU 单价和数量计算商品合计；运费 SHALL 为零。

#### Scenario: Buyer selects all cart items
- **WHEN** 买家执行全选或全不选
- **THEN** 系统更新全部条目的选择状态并返回对应的已选商品合计

#### Scenario: Selected subtotal reflects current prices
- **WHEN** 买家改变条目选择或数量，或读取购物车
- **THEN** 已选商品金额按当前 SKU 单价乘数量重新计算，不把旧价格作为结算价格

### Requirement: Checkout validation does not create an order
系统 SHALL 在买家请求校验已选购物车条目时重新检查商品/SKU 可售状态、当前价格和可售库存；不可购买或价格不同于买家最近接受基准的条目 SHALL 阻止校验通过并指出需处理条目。买家明确接受新价格后，系统 SHALL 更新该条目的价格基准并重新校验。本次校验 SHALL 不创建订单、不预留库存。

#### Scenario: Selected item changes before checkout validation
- **WHEN** 任一已选 SKU 已下架、停用、改价或库存不足
- **THEN** 系统返回需要处理的条目和最新状态，不创建订单且不预留库存

#### Scenario: Selected items remain purchasable
- **WHEN** 所有已选条目仍可售且数量未超过当前库存
- **THEN** 系统返回最新商品金额及运费为零的校验结果，不创建订单且不预留库存

#### Scenario: Buyer accepts a changed price
- **WHEN** 买家明确接受购物车条目的当前新价格
- **THEN** 系统更新该条目的价格基准，并按当前价格和库存重新执行结算校验
