# Spec Delta

## Purpose

让真实购买并完成收货的买家分享水果商品体验，并让其他买家在商品详情中查看可追溯到已购商品的评价。

## ADDED Requirements

### Requirement: Buyer reviews purchased products after completion
系统 SHALL 允许买家对已完成订单中的已购 SKU 提交一次 1 至 5 星评分和可选文字评价。系统 SHALL 拒绝未完成订单、未购买 SKU 或重复提交的评价；本版本不支持评价图片、追评或商家回复。

#### Scenario: Buyer submits a valid review
- **WHEN** 买家为自己已完成订单中的 SKU 提交有效评分和可选文字
- **THEN** 系统保存评价并将其关联到该买家、订单明细和 SKU

#### Scenario: Buyer attempts a duplicate review
- **WHEN** 买家再次评价同一订单明细中的 SKU
- **THEN** 系统拒绝重复评价并保留原评价

### Requirement: Product details display reviews
系统 SHALL 在商品详情中展示该商品已发布的评价列表及评分摘要，并支持分页读取评价。

#### Scenario: Buyer views product reviews
- **WHEN** 买家打开商品评价区域
- **THEN** 系统展示该商品关联的评分、文字和评价时间，且不展示其他商品的评价
