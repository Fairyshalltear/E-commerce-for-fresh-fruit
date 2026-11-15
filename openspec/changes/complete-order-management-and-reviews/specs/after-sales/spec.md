# Spec Delta

## Purpose

为订单提供可追踪的部分或整单退款、退货申请和管理员处理流程。申请、审批、退回收货、库存处理和模拟退款均须保留关联记录，避免重复退款或库存变更。

## ADDED Requirements

### Requirement: Buyer requests an eligible refund or return
系统 SHALL 允许买家针对本人订单的指定明细及数量提交售后申请，保存类型、原因、说明、金额和处理状态。已付款未发货订单可申请退款；已发货订单仅可在确认收货后 7 天内申请退货退款。

#### Scenario: Buyer requests an unshipped refund
- **WHEN** 买家为本人已付款且待发货订单选择未售后处理的商品数量并提交退款申请
- **THEN** 系统创建待审核退款申请，金额按订单商品快照计算，不改变库存或支付状态

#### Scenario: Buyer requests a return within the allowed period
- **WHEN** 买家为本人已完成订单在确认收货后 7 天内选择未售后处理的商品数量并提交退货退款申请
- **THEN** 系统创建待审核退货申请并关联订单明细、数量和快照金额

#### Scenario: Buyer requests an ineligible or excessive quantity
- **WHEN** 订单状态不符合申请类型、申请超过 7 天、数量超过未处理可申请数量或退款金额无效
- **THEN** 系统拒绝申请且不创建售后记录

### Requirement: Administrators review after-sales applications
系统 SHALL 允许管理员分页查看并筛选售后申请，批准或拒绝待审核申请并记录处理人、时间及原因。

#### Scenario: Administrator approves a refund before dispatch
- **WHEN** 管理员批准有效的未发货退款申请
- **THEN** 系统记录模拟退款、释放对应订单商品的预留库存，并将申请标记为已完成

#### Scenario: Administrator approves a return
- **WHEN** 管理员批准有效的已收货退货申请
- **THEN** 系统将申请转为等待买家寄回，且不提前增加库存或完成退款

#### Scenario: Administrator rejects an application
- **WHEN** 管理员拒绝待审核申请并填写原因
- **THEN** 系统保存拒绝原因并结束该申请，不变更库存或执行退款

### Requirement: Buyer registers return shipment
系统 SHALL 允许已批准退货申请的买家登记寄回快递公司和运单号；不得伪造或自动生成真实物流状态。

#### Scenario: Buyer submits return tracking information
- **WHEN** 买家为本人已批准且等待寄回的申请提交有效快递公司及运单号
- **THEN** 系统保存寄回信息并将申请转为等待管理员收货确认

#### Scenario: Buyer cannot update another buyer's return
- **WHEN** 买家尝试读取或修改不属于自己的退货申请物流信息
- **THEN** 系统拒绝访问且不泄露申请信息

### Requirement: Administrators receive returned goods and complete refunds
系统 SHALL 允许管理员对已登记寄回信息的退货确认收货，分别记录退款数量和实际可入库数量；二者均不得超过获批申请数量。系统按订单快照单价计算模拟退款，且仅为确认可入库的数量增加实物库存并记录库存流水。

#### Scenario: Administrator receives and refunds a return
- **WHEN** 管理员为等待收货申请登记不超过申请数量的可入库数量并确认处理
- **THEN** 系统增加相应 SKU 实物库存，记录退货入库流水及模拟退款，并完成申请

#### Scenario: Administrator receives no resalable quantity
- **WHEN** 管理员确认退货但登记可入库数量为零
- **THEN** 系统不增加实物库存，仍按确认的退款数量记录模拟退款和收货处理

#### Scenario: Administrator attempts duplicate completion
- **WHEN** 管理员重复确认已经完成的退款或退货申请
- **THEN** 系统拒绝重复退款、库存变更或重复完成记录
