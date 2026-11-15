# Spec Delta

## Purpose

使买家能从商品选择进入可追踪的下单、支付和收货流程，并使管理员能够履约和维护物流状态。订单必须保留购买时的关键快照，并与 SKU 库存保持一致。

## ADDED Requirements

### Requirement: Buyer confirms a cart or immediate-purchase order
系统 SHALL 为已登录买家提供购物车选中商品结算和商品详情立即购买入口；两种入口 SHALL 使用相同的确认订单行为。

#### Scenario: Buyer reviews checkout details
- **WHEN** 买家从购物车或立即购买进入确认订单
- **THEN** 系统展示可选择的本人收货地址、商品规格与数量、当前单价、金额和零运费合计，并重新校验商品状态、买家已接受价格和可售库存

#### Scenario: Checkout data is invalid
- **WHEN** 商品已失效、库存不足、价格尚未确认或收货地址不属于当前买家
- **THEN** 系统阻止提交并返回需要处理的项目，不创建订单或预留库存

### Requirement: Order submission snapshots purchase facts and reserves stock
系统 SHALL 在一个原子操作中创建待付款订单及商品、规格、价格、数量、金额和收货信息快照，并预留所需 SKU 库存。提交失败 SHALL 不留下部分订单、部分库存预留或部分清理的购物车。

#### Scenario: Buyer submits a valid order
- **WHEN** 买家提交仍可购买的商品和本人有效地址
- **THEN** 系统创建待付款订单，保存下单时快照，预留全部商品数量，并仅移除本次从购物车购买的条目

#### Scenario: Concurrent submissions exceed available stock
- **WHEN** 并发订单对同一 SKU 的总需求超过可售库存
- **THEN** 系统仅接受可被完整满足的订单，其余订单整体失败且不发生超卖或部分预留

#### Scenario: Catalog or address changes after purchase
- **WHEN** 下单后商品、规格、价格或买家地址发生变化
- **THEN** 历史订单仍展示下单时保存的商品、规格、金额和收货信息

### Requirement: Buyer simulates payment and unpaid orders expire
系统 SHALL 允许买家为待付款订单选择支付宝或微信模拟支付，并提供成功、失败和取消结果。待付款订单 SHALL 在默认 15 分钟后自动取消，期限可配置。

#### Scenario: Simulated payment succeeds
- **WHEN** 买家为本人有效待付款订单确认支付宝或微信模拟支付成功
- **THEN** 系统记录渠道及支付时间并将订单转为待发货，重复请求不得重复记账

#### Scenario: Simulated payment fails or is cancelled
- **WHEN** 买家选择模拟支付失败或取消
- **THEN** 订单保持待付款且库存继续预留，系统展示相应结果

#### Scenario: Unpaid order is cancelled or expires
- **WHEN** 买家取消本人待付款订单，或订单达到配置的未付款时限
- **THEN** 系统将订单标记为已取消并释放预留库存一次，保留订单记录

### Requirement: Buyer lists and views owned orders
系统 SHALL 为买家提供全部、待付款、待发货、待收货、已完成和已取消订单列表，并提供本人订单详情及状态时间线。

#### Scenario: Buyer filters orders by status
- **WHEN** 买家选择订单状态并请求某一分页
- **THEN** 系统仅返回该买家的匹配订单、总数和分页信息，全部筛选包含其所有状态订单

#### Scenario: Buyer requests another buyer's order
- **WHEN** 买家读取不属于自己的订单详情或执行订单操作
- **THEN** 系统拒绝访问且不泄露订单内容

### Requirement: Buyer confirms receipt
系统 SHALL 允许买家确认本人待收货订单，且仅将该订单转为已完成。

#### Scenario: Buyer confirms a shipped order
- **WHEN** 买家确认本人待收货订单已收货
- **THEN** 系统记录完成时间并将订单转为已完成，允许对符合条件的订单商品提交评价

### Requirement: Administrators search, filter, and dispatch orders
系统 SHALL 允许管理员对订单分页查询，按状态、下单时间范围和订单号/买家关键词筛选，并查看订单快照。管理员 SHALL 可为待发货订单登记快递公司和运单号后发货。

#### Scenario: Administrator filters orders
- **WHEN** 管理员请求包含分页及有效筛选条件的订单列表
- **THEN** 系统返回匹配订单、总数和分页信息，且仅 ADMIN 可访问

#### Scenario: Administrator dispatches an order
- **WHEN** 管理员为待发货订单登记快递公司和有效运单号并确认发货
- **THEN** 系统记录发货信息、扣减对应 SKU 实物库存和预留库存、增加状态记录并将订单转为待收货

#### Scenario: Administrator attempts duplicate or invalid dispatch
- **WHEN** 管理员重复发货或尝试发货非待发货订单
- **THEN** 系统拒绝重复库存变更并保留原订单状态和物流数据

### Requirement: Orders expose a chronological tracking history
系统 SHALL 为订单保留创建、支付、发货、确认收货、取消等状态事件，并允许管理员维护模拟物流节点；买家 SHALL 只能查看本人订单的物流信息。

#### Scenario: Administrator adds a tracking event
- **WHEN** 管理员为已发货订单添加物流描述和发生时间
- **THEN** 系统持久化节点，订单详情按发生时间展示轨迹

#### Scenario: Buyer views order tracking
- **WHEN** 买家查看本人已发货订单
- **THEN** 系统展示快递公司、运单号、状态时间线和已登记的物流节点，不调用真实物流服务
