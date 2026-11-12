# Spec Delta

## Purpose

为水果质量或订单问题提供可追踪的退款与退货处理路径，允许买家提交申请、管理员审核，并在答辩环境中完成不依赖支付或快递服务的闭环。

## ADDED Requirements

### Requirement: Buyer submits an after-sales request
系统 SHALL 允许买家为自己的已支付订单提交未发货退款申请，或为已发货订单提交退货退款申请，并填写原因和说明。系统 SHALL 保存申请类型、关联订单明细、申请状态、审核记录和处理时间。

#### Scenario: Buyer requests refund before dispatch
- **WHEN** 买家为自己的已支付且尚未发货订单提交退款申请
- **THEN** 系统创建待审核售后申请并关联该订单

#### Scenario: Buyer requests return after dispatch
- **WHEN** 买家为自己的已发货订单提交退货退款申请
- **THEN** 系统创建待审核退货申请且不伪造物流信息

### Requirement: Administrator reviews and simulates refunds
系统 SHALL 允许管理员查看待处理申请、批准或拒绝申请并记录原因。未发货退款获批后，系统 SHALL 记录模拟退款结果并释放该订单预留库存；系统 SHALL 不调用真实支付渠道。

#### Scenario: Administrator approves an unshipped refund
- **WHEN** 管理员批准一个未发货退款申请
- **THEN** 系统记录模拟退款、释放对应 SKU 预留数量并将售后申请标记为已完成

#### Scenario: Administrator rejects a refund request
- **WHEN** 管理员拒绝一个待审核申请并填写原因
- **THEN** 系统保存拒绝原因并通知买家，且不执行退款或库存变更

### Requirement: Returned goods are received and restocked
系统 SHALL 允许管理员在已发货退货申请获批后登记线下寄回商品的收货结果。确认收货并登记可入库数量后，系统 SHALL 增加对应 SKU 实物库存、写入退货入库流水并记录模拟退款，随后将售后申请标记为已完成。

#### Scenario: Administrator receives an approved return
- **WHEN** 管理员为已批准退货登记收货数量并确认模拟退款
- **THEN** 系统增加可入库 SKU 数量、保留售后与库存审计记录，并完成该售后申请

#### Scenario: Return has not been received
- **WHEN** 退货申请已获批但管理员尚未登记收货
- **THEN** 系统保持申请为待退货收货状态且不增加库存或标记退款完成
