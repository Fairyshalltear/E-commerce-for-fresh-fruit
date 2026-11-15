# Spec Delta

## Purpose

让完成购买的买家评价订单中的商品，并让其他买家查看评价内容和评分摘要。评价治理须保留删除审计，同时使被删除内容不再影响公开展示或评分。

## ADDED Requirements

### Requirement: Buyer reviews an item from a completed order
系统 SHALL 允许买家对本人已完成订单中的每个订单明细提交一次 1 至 5 星评分和可选文字；买家提交后 SHALL 不得自行修改或删除评价。

#### Scenario: Buyer submits a review
- **WHEN** 买家为本人已完成订单中尚未评价的订单明细提交有效评分和可选文字
- **THEN** 系统保存评价并关联买家、订单明细、SKU 和商品

#### Scenario: Buyer reviews an ineligible or already-reviewed item
- **WHEN** 买家评价未完成订单、未购买的商品或已评价的订单明细
- **THEN** 系统拒绝写入并保留已有评价

#### Scenario: Buyer attempts to edit or delete a submitted review
- **WHEN** 买家请求修改或删除本人已提交的评价
- **THEN** 系统拒绝该操作且不改变评价内容

### Requirement: Public product reviews and summaries exclude deleted reviews
系统 SHALL 按商品分页展示有效评价，并基于有效评价计算评分条数与均分；公开响应 SHALL 不包含买家敏感信息、订单标识或删除审计信息。

#### Scenario: Buyer views product reviews
- **WHEN** 用户查看商品评价列表或评分摘要
- **THEN** 系统仅展示该商品有效评价的评分、文字、规格快照、展示昵称和时间，并返回匹配的有效评价数量与均分

#### Scenario: Product has no effective reviews
- **WHEN** 商品没有有效评价，包括全部评价均已删除的情况
- **THEN** 系统返回零条评价且平均评分为空

### Requirement: Administrators delete inappropriate reviews with an audit trail
系统 SHALL 允许管理员基于违规、辱骂或无关等原因删除评价，并记录管理员、原因、时间及被删除评价的必要快照。删除 SHALL 为可审计的逻辑删除，不得从审计记录中物理抹除原评价。

#### Scenario: Administrator deletes a review with a reason
- **WHEN** 管理员为有效评价提交删除原因并确认删除
- **THEN** 系统将其标记为已删除，保存原评价和删除审计，并从商品评价列表及评分摘要中排除

#### Scenario: Administrator omits a deletion reason
- **WHEN** 管理员未提供有效删除原因
- **THEN** 系统拒绝删除且不产生审计记录

#### Scenario: Administrator repeats deletion
- **WHEN** 管理员尝试再次删除已删除评价
- **THEN** 系统不重复创建删除记录，且评价继续从公开列表和评分摘要排除
