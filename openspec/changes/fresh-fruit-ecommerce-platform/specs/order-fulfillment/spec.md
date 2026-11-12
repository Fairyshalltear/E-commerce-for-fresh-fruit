# Spec Delta

## Purpose

覆盖从买家确认结算到订单收货完成的交易履约过程，并以明确的状态转换保持订单、支付、物流和 SKU 库存记录一致。

## ADDED Requirements

### Requirement: Checkout creates an order and reserves inventory
系统 SHALL 允许已登录买家选择收货地址并提交有效购物车条目，生成待付款订单及商品价格、规格和地址快照。提交时系统 SHALL 原子地预留每个 SKU 所需份数；可售库存 SHALL 等于实物库存减去预留库存。运费 SHALL 为零。

#### Scenario: Buyer submits a valid order
- **WHEN** 买家提交有效地址和仍可售的购物车条目
- **THEN** 系统创建待付款订单、保存结算快照、预留对应库存并清除已购买的购物车条目

#### Scenario: Concurrent checkout exceeds available stock
- **WHEN** 多个买家同时提交订单且所需 SKU 总份数超过可售库存
- **THEN** 系统只成功预留不超过可售库存的订单，其余请求失败且不产生部分预留

### Requirement: Mock payment and unpaid-order expiration
系统 SHALL 在待付款订单上提供支付宝或微信的模拟支付选择；买家触发模拟成功操作后，系统 SHALL 记录模拟支付并将订单转为待发货。系统 SHALL 在配置的未付款时限到达后取消订单并释放其预留库存，默认时限为 15 分钟。

#### Scenario: Buyer completes mock payment
- **WHEN** 买家为有效待付款订单选择支付宝或微信并确认模拟支付成功
- **THEN** 系统记录所选模拟渠道和支付时间，并将订单转为待发货

#### Scenario: Unpaid order expires
- **WHEN** 待付款订单超过配置时限仍未支付
- **THEN** 系统将订单取消一次、释放其 SKU 预留数量并保留订单记录

### Requirement: Buyer views and cancels orders
系统 SHALL 为买家提供按全部、待付款、待发货、待收货、已完成和已取消状态筛选的订单列表及订单详情。买家 SHALL 可取消自己的待付款订单并确认自己的待收货订单；买家不得取消其他状态或其他用户的订单。

#### Scenario: Buyer cancels an unpaid order
- **WHEN** 买家取消自己的待付款订单
- **THEN** 系统将订单标记为已取消并释放全部预留库存

#### Scenario: Buyer confirms receipt
- **WHEN** 买家确认自己的待收货订单已收货
- **THEN** 系统将订单标记为已完成

### Requirement: Administrator dispatches orders and maintains simulated tracking
系统 SHALL 允许管理员查看和筛选订单详情，并为待发货订单登记快递公司及运单号后发货。发货时系统 SHALL 扣减对应 SKU 实物库存和预留库存，并将订单转为待收货。管理员 SHALL 能为订单添加模拟物流节点，买家 SHALL 能查看这些节点；系统 SHALL 不依赖第三方快递查询服务。

#### Scenario: Administrator dispatches a paid order
- **WHEN** 管理员为待发货订单填写快递信息并确认发货
- **THEN** 系统记录运单信息、扣减实物库存与预留库存，并将订单转为待收货

#### Scenario: Buyer views simulated tracking events
- **WHEN** 买家打开自己的已发货订单
- **THEN** 系统展示管理员登记的物流节点及其时间顺序
