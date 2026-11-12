# Spec Delta

## Purpose

为买家提供可检索、可比较的水果商品目录，并让管理员维护分类、商品和可售规格，确保页面展示与可售库存一致。

## ADDED Requirements

### Requirement: Administrator manages fruit categories and products
系统 SHALL 允许管理员维护最多两级的商品分类，并对水果商品及其 SKU 执行新增、编辑、上架和下架。商品信息 SHALL 支持名称、图片、描述、产地、口感、储存方法及商品级展示信息；每个可售 SKU SHALL 有独立规格、价格、汇总库存和低库存阈值。

#### Scenario: Administrator publishes a product with multiple SKUs
- **WHEN** 管理员为一个水果商品建立多个规格并将商品上架
- **THEN** 每个 SKU 的规格与价格分别保存，已上架商品可在买家目录中展示

#### Scenario: Unlisted product is unavailable for purchase
- **WHEN** 管理员下架一个商品或其所属分类不可用
- **THEN** 买家目录不将该商品作为可购买商品返回

### Requirement: Buyer browses and searches the catalog
系统 SHALL 为买家提供首页商品展示、分类浏览、关键词搜索、分类筛选和价格区间筛选。商品详情 SHALL 展示商品图片、描述、产地、口感、储存方法、SKU 规格与价格、当前可售数量及评价摘要。

#### Scenario: Buyer filters products by category and price
- **WHEN** 买家提交分类和有效的最低及最高价格条件
- **THEN** 系统只返回已上架且符合全部筛选条件的商品

#### Scenario: Buyer opens a product detail
- **WHEN** 买家查看一个已上架商品
- **THEN** 系统返回其商品信息、可售 SKU 和可售库存，不暴露未上架商品

### Requirement: Buyer manages product favorites
系统 SHALL 允许已登录买家收藏或取消收藏商品，并提供其收藏商品列表。

#### Scenario: Buyer removes a favorite
- **WHEN** 买家取消收藏一个已收藏商品
- **THEN** 该商品从该买家的收藏列表移除，商品本身及其他买家的收藏不受影响
