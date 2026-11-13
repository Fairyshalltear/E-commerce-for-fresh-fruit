# Spec Delta

## Purpose

为商品详情提供来自该商品已发布评价的评分摘要和分页列表，使买家能够查看评分、文字、规格和评价时间。本阶段仅建设读取与空状态，保留评价与已购商品及订单明细的关联边界，评价提交将在订单完成校验实现后单独开放。

## ADDED Requirements

### Requirement: Product details display published reviews and rating summaries
系统 SHALL 为公开可见商品展示已发布评价总数及平均星级，并分页提供评分、可选文字、评价时的规格、评价时间和买家展示昵称。评分 SHALL 为 1 至 5 星；平均星级 SHALL 基于全部已发布评价计算，按一位小数四舍五入，而不是只统计当前页。未发布评价和其他商品评价 SHALL 不计入列表或摘要。

#### Scenario: Published reviews are paginated and summarized
- **WHEN** 用户读取某商品的评价，其中该商品有评分 4 和 5 的两条已发布评价及一条未发布评价
- **THEN** 列表只包含两条已发布评价，按评价时间倒序和评价标识倒序分页，摘要总数为 2、平均星级为 4.5

#### Scenario: Reviews from another product are excluded
- **WHEN** 用户请求商品 A 的评价，系统另有商品 B 的已发布评价
- **THEN** 返回列表及评分摘要均不包含商品 B 的评价

### Requirement: Empty reviews and unavailable products have explicit behavior
无已发布评价时系统 SHALL 返回总数 0、空列表和空值平均星级，前端展示“暂无评价”，不得把 0 星当作实际评分。下架、删除或分类不可用商品的公开评价读取 SHALL 返回与公开商品详情一致的不存在响应。

#### Scenario: Product has no published reviews
- **WHEN** 用户打开没有已发布评价的商品详情
- **THEN** 页面展示暂无评价，摘要为 0 条、平均星级为空，商品其他信息正常展示

#### Scenario: Product becomes unavailable before reviews are loaded
- **WHEN** 用户请求已经下架商品的评价
- **THEN** 系统返回商品不可用的不存在响应，不返回历史评价内容

### Requirement: Review data is private and read-only in this stage
公开评价 SHALL 不暴露买家手机号、用户名、账户标识、订单标识或服务器内部数据；文字 SHALL 按普通文本展示。当前版本 SHALL 不提供评价创建、编辑、删除或管理员代写入口，不通过公开写接口生成演示评价。未来提交评价 SHALL 以本人已完成订单明细为依据，本阶段不改变此已购评价约束。

#### Scenario: Review display excludes sensitive account data
- **WHEN** 用户读取一条评价
- **THEN** 响应只包含评价展示所需字段，文字中的 HTML 字符按文本展示，不执行脚本

#### Scenario: Client attempts to create a review
- **WHEN** 买家或管理员尝试通过商品评价读取地址提交新评价
- **THEN** 系统不提供可用写操作，不新增评价；前端无评价提交按钮
