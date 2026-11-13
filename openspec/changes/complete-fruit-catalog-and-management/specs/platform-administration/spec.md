# Spec Delta

## Purpose

为本阶段商品、分类和库存管理保存最小必要的管理员操作记录，使成功变更能够追溯到操作者、对象和时间，并区分被拒绝的操作。本阶段仅覆盖商品相关管理操作的记录与保护，不开放日志查询页面或其他业务领域的管理功能。

## ADDED Requirements

### Requirement: Catalog management changes have durable administrator records
系统 SHALL 为成功的商品新增、编辑、上下架、逻辑删除、分类新增/编辑/排序/启停/删除及商品编辑中的库存调整保存管理员身份、操作类型、目标标识、时间和成功结果。操作记录 SHALL 与对应业务变更原子保存；库存流水 SHALL 保留自身调整明细并关联对应管理操作。重复设置相同状态 SHALL 不产生重复变更记录。

#### Scenario: Administrator publishes a product
- **WHEN** 管理员成功将商品从下架改为上架
- **THEN** 系统同时保存商品状态及带操作者、商品标识、时间和成功结果的上架操作记录

#### Scenario: Audit persistence fails
- **WHEN** 管理操作在保存成功记录时发生持久化失败
- **THEN** 系统回滚同次业务修改，不出现已经修改但缺少成功操作记录的状态

#### Scenario: Management operation is rejected
- **WHEN** 管理员因无效分类或库存限制而被拒绝保存
- **THEN** 业务数据不改变，不写成功操作记录，并在受控服务日志记录失败类型和已知目标标识

### Requirement: Administrative records exclude credentials and buyer exposure
管理员操作记录和失败日志 SHALL 不保存密码、JWT、认证头或完整请求体。商城商品、评价及收藏响应 SHALL 不包含管理员操作记录，访客与买家 SHALL 不得读取或修改此类记录。本阶段 SHALL 不开放操作记录写入/删除 API 或审计查询页面。

#### Scenario: Catalog response is inspected by a buyer
- **WHEN** 买家读取商品、评价或收藏数据
- **THEN** 响应不包含内部操作记录、管理员标识或认证凭据

#### Scenario: Client attempts to modify operation records
- **WHEN** 访客或买家尝试直接写入或删除管理员操作记录
- **THEN** 系统不提供可用操作，不修改任何记录
