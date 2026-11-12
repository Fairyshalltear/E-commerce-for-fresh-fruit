# Spec Delta

## Purpose

为管理员提供对关键后台变更的可追溯记录和查询能力，便于演示系统治理流程并定位商品、订单、库存和账户操作。

## ADDED Requirements

### Requirement: System records administrative operations
系统 SHALL 为管理员对商品、分类、订单、售后、库存、用户、站点内容和 AI 知识的新增、修改、删除及状态变更记录操作者、操作类型、目标对象、时间和结果。日志 SHALL 不保存明文密码、访问令牌或模型密钥。

#### Scenario: Administrator changes inventory
- **WHEN** 管理员提交一笔库存入库、出库或盘点调整
- **THEN** 系统记录操作者、SKU、操作类型、数量变化和操作时间

### Requirement: Administrator searches operation logs
系统 SHALL 允许已认证管理员按时间范围、操作者、操作类型和结果筛选操作日志，并分页查看日志详情。

#### Scenario: Administrator filters logs by target operation
- **WHEN** 管理员按操作类型和时间范围查询审计记录
- **THEN** 系统仅返回符合条件的日志并按时间倒序排列

### Requirement: Operation logs are protected from buyer access
系统 SHALL 拒绝访客和买家访问后台操作日志，并 SHALL 不提供通过商城 API 修改或删除审计记录的能力。

#### Scenario: Buyer requests an operation log
- **WHEN** 买家请求后台操作日志
- **THEN** 系统拒绝请求且不返回任何日志数据
