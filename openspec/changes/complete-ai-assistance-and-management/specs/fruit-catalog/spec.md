# Spec Delta

## Purpose

扩展商品咨询及推荐所需的甜度、应季月份、礼盒和适用场景资料，让管理员可以维护真实可选属性，并使商城展示、推荐筛选及关联知识与商品当前状态保持一致。

## ADDED Requirements

### Requirement: Administrator maintains optional consultation attributes
系统 SHALL 在商品编辑及详情中支持甜度文字描述、应季月份集合、礼盒标记及适用场景集合。月份 SHALL 仅为一至十二且去重；适用场景 SHALL 使用明确的可选枚举及展示名称；甜度 SHALL 保持文字描述，不默认解释为实测糖度。未填写 SHALL 表示未知，不自动赋予应季或送礼属性。

#### Scenario: Administrator saves seasonal gift metadata
- **WHEN** 管理员为商品填写甜度描述、十一月/十二月、礼盒和适用场景
- **THEN** 保存后编辑页、详情与咨询使用同一组资料，月份和场景不重复

#### Scenario: Seasonal months are invalid
- **WHEN** 保存包含零、十三、非整数或未知场景的资料
- **THEN** 系统拒绝该保存并说明字段错误，原资料保持不变

### Requirement: New fields preserve old catalog data and requests
系统 SHALL 保留既有商品、SKU、价格、库存及状态，新增字段不要求批量补齐。商品旧客户端未提供新增字段时 SHALL 保留已有扩展属性；显式清空属性 SHALL 使用可区分的字段值。旧数据的月份/场景未知、礼盒未标记 SHALL 不被自动推断为匹配推荐条件。

#### Scenario: Existing products are upgraded
- **WHEN** 对已有商品数据库应用新增字段迁移
- **THEN** 商品仍可浏览与购买，新增属性为空或未标记，已有业务数据不变

#### Scenario: An older edit request omits the new properties
- **WHEN** 已有扩展属性的商品通过未包含新增字段的旧请求保存其他资料
- **THEN** 扩展属性保持原值，不被默认空集合覆盖

### Requirement: Public display and AI metadata respect catalog visibility
系统 SHALL 仅对公共可见商品展示当前新增资料，资料缺失时隐藏空项或显示尚未提供。用于推荐的礼盒、应季及场景条件 SHALL 由服务端按真实字段判断；商品、SKU 或分类失效后不得因旧扩展属性继续进入推荐。

#### Scenario: User asks for gift boxes from public products
- **WHEN** 用户请求本月礼盒推荐
- **THEN** 服务端只从明确标记礼盒且应季月份包含本月的当前可售商品中筛选

#### Scenario: Product has no sweetness information
- **WHEN** 用户打开未维护甜度资料的商品详情并咨询甜度
- **THEN** 详情不展示虚构描述，AI 说明尚未提供该资料
