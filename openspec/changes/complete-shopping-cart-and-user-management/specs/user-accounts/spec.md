# Spec Delta

## Purpose

让已认证管理员能够安全、可追溯地查询和维护买家账户，并使账户禁用或密码重置后已签发的买家会话立即失效。管理员账户不属于本能力的管理对象。

## ADDED Requirements

### Requirement: Administrator searches paginated buyer accounts
系统 SHALL 允许已认证管理员按关键词和账户状态筛选买家账户，并分页查看结果；关键词 SHALL 匹配用户名、手机号或昵称，响应 SHALL 不包含密码或密码哈希。

#### Scenario: Administrator searches buyers
- **WHEN** 管理员提交关键词、状态筛选和有效分页参数
- **THEN** 系统仅返回匹配的买家账户摘要及分页信息，且不返回凭据

#### Scenario: Buyer attempts to search managed accounts
- **WHEN** 买家请求管理员买家列表
- **THEN** 系统拒绝请求且不返回买家管理数据

### Requirement: Administrator views buyer account details
系统 SHALL 允许已认证管理员查看单个买家的基础资料、账户状态和注册时间；详情 SHALL 不包含密码、密码哈希、订单信息或收货地址。

#### Scenario: Administrator views a buyer
- **WHEN** 管理员请求存在的买家详情
- **THEN** 系统返回该买家的基础资料、状态和注册时间，不返回订单、地址或凭据

### Requirement: Administrator enables or disables buyer accounts
系统 SHALL 允许已认证管理员启用或禁用买家账户，并为成功的状态变更记录管理员、操作类型、目标账户和时间；禁用 SHALL 立即使该账户所有已有 JWT 失效。

#### Scenario: Administrator disables a buyer with an active token
- **WHEN** 管理员禁用买家账户后，该买家使用先前签发的 JWT 请求受保护接口
- **THEN** 系统拒绝该请求，且账户与历史业务数据仍被保留

#### Scenario: Administrator repeats the current status
- **WHEN** 管理员将账户设置为其当前状态
- **THEN** 系统保持状态不变且不生成重复的成功状态变更审计记录

### Requirement: Administrator resets buyer password
系统 SHALL 允许已认证管理员设置买家的临时密码；系统 SHALL 仅保存密码哈希，审计记录 SHALL 不包含密码或其哈希，成功重置 SHALL 立即使该买家所有已有 JWT 失效。

#### Scenario: Administrator resets a buyer password
- **WHEN** 管理员提交符合密码策略的临时密码并确认重置
- **THEN** 系统保存新密码哈希、记录不含凭据的成功审计事件，并立即拒绝该买家先前签发的 JWT

#### Scenario: Invalid temporary password is submitted
- **WHEN** 管理员提交不符合密码策略的临时密码
- **THEN** 系统拒绝重置，不改变买家密码且不写入成功审计记录
