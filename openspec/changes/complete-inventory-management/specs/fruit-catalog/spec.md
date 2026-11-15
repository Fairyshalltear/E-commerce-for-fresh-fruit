# Spec Delta

## Purpose

明确商品维护与库存作业的职责边界，使商品创建能够初始化新规格库存，后续商品内容和价格编辑不会覆盖订单预留或已完成的出入库结果。已有库存和阈值仍可查看，变更统一交由可追溯的库存管理流程。

## ADDED Requirements

### Requirement: Product creation initializes only new SKU stock
商品新增及编辑中添加全新 SKU 时，系统 SHALL 允许设置非负整数初始库存，预留初始化为 0、低库存阈值初始化为 0。正数初始库存 SHALL 产生初始化流水；零初始库存 SHALL 不产生数量为零的流水。初始化 SHALL 与商品、SKU 和成功审计原子保存；恢复或改名已有 SKU SHALL 不被当作新 SKU 重置库存。

#### Scenario: Administrator creates stock for a new specification
- **WHEN** 管理员新增苹果商品或为已有商品新增 SKU，设置初始库存 20
- **THEN** 新 SKU 实物为 20、预留为 0、阈值为 0，保存 +20 初始化流水，商品页面提示日后在库存管理维护库存和阈值

#### Scenario: Restoring a retired SKU preserves inventory
- **WHEN** 管理员重新启用已有停用 SKU 或修改其名称及价格
- **THEN** 保持原 SKU 标识、实物、预留和阈值，不重新初始化或产生初始化流水

### Requirement: Existing product stock is read only in catalog management
商品编辑 SHALL 展示已有 SKU 当前实物、预留、可售数量和阈值，但不提供已有库存、阈值或库存调整原因的编辑入口。商品维护 SHALL 允许继续编辑名称、规格、价格、图片等商品信息；库存页面 SHALL 提供明确导航入口。系统 SHALL 保留有预留库存时禁止停用、移除 SKU 或删除商品的既有约束。

#### Scenario: Administrator edits an existing SKU price
- **WHEN** 管理员修改已有 SKU 售价并保存商品
- **THEN** 价格更新成功，已有实物、预留和阈值不变，不新增库存调整流水

#### Scenario: Existing SKU has outstanding orders
- **WHEN** 管理员试图停用或移除仍有预留库存的 SKU
- **THEN** 系统拒绝商品保存，保留商品、SKU 和全部库存状态

### Requirement: Product API cannot bypass inventory operations
商品保存接口 SHALL 根据 SKU 是否已有标识区分初始库存字段：新 SKU 必须提供合法初始库存，已有 SKU SHALL 不接受客户端指定实物、预留或阈值。已有库存版本 SHALL 不作为普通商品信息保存的前提，商品和 SKU 的并发编辑及预留保护仍须校验。旧客户端提交已有 SKU 库存字段 SHALL 返回明确校验错误，不能静默覆盖或忽略。

#### Scenario: Legacy payload tries to overwrite existing inventory
- **WHEN** 商品保存请求为已有 SKU 提交 onHand、reserved 或 lowStockThreshold 字段
- **THEN** 系统拒绝整次保存并提示库存字段只允许通过库存管理维护，商品与所有库存保持不变

#### Scenario: Stock changes while product description is edited
- **WHEN** 管理员读取商品后发生订单预留或采购入库，管理员随后仅提交商品描述和已有 SKU 商品字段
- **THEN** 在商品自身版本仍有效且其他约束满足时保存成功，保留最新库存，不要求将旧库存值重新提交

### Requirement: Historical catalog stock adjustments remain auditable
系统 SHALL 保留升级前初始库存和商品编辑调整流水，不回算或重置当前库存。新版本 SHALL 不再通过已有商品保存生成 PRODUCT_EDIT_ADJUSTMENT 数量流水；历史记录仍可在库存流水中查询。

#### Scenario: Existing product is opened after upgrade
- **WHEN** 管理员在升级后打开已有商品并查看其库存历史
- **THEN** 原库存数值和历史编辑调整流水仍存在，商品表单的库存只读，后续调整由库存单据或盘点处理
