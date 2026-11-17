# Spec Delta

## Purpose

将 AI 客服后台纳入现有店主与店员授权体系，使知识、规则、记录及统计按当前模块权限开放，并把外部模型、凭据及提示词配置限制在店主边界内，所有关键操作均可审计。

## ADDED Requirements

### Requirement: AI administration has an explicit module permission
系统 SHALL 新增 AI 模块权限及对应的后台菜单、路由和接口授权分类；店主默认可访问，店员必须由店主显式授权才能查看或维护知识、规则、脱敏记录和 AI 统计。现有店员 SHALL 不自动获得新增权限，未知或未分类后台 AI 接口 SHALL 默认拒绝。

#### Scenario: Owner grants AI access to a staff account
- **WHEN** 店主为店员启用 AI 模块权限
- **THEN** 店员可通过前端和直接 API 访问授权的 AI 页面及操作，权限展示与服务端结果一致

#### Scenario: Existing staff account is migrated
- **WHEN** 系统升级增加 AI 权限项
- **THEN** 该店员保持原有权限，未经授予不能访问 AI 后台

### Requirement: Model configuration remains owner-only
系统 SHALL 将模型/API Key、生成与向量参数、提示词、服务启停、连接测试和索引模型切换限定为店主操作。有 AI 模块权限的店员 SHALL 无法读取模型配置详情、写入凭据或触发带新凭据的连接测试。

#### Scenario: AI-authorized staff calls the model configuration endpoint
- **WHEN** 获得 AI 模块权限的店员直接请求模型配置或连接测试 API
- **THEN** 系统拒绝并记录拒绝事件，不发起模型调用或改变配置

### Requirement: Permission revocation takes effect on subsequent AI actions
系统 SHALL 按数据库当前账户状态与权限校验后台操作，不只依赖旧令牌和前端菜单。店员撤权或停用后 SHALL 拒绝后续知识、规则、记录和统计访问；切换页面或收到拒绝时前端 SHALL 清除该页可访问的敏感详情状态。

#### Scenario: Staff loses access while using an older token
- **WHEN** 店主撤销 AI 权限，店员持原 JWT 继续请求对话详情
- **THEN** 服务端拒绝读取并不返回原正文，前端呈现失去权限状态

### Requirement: AI administration integrates existing audit behavior
系统 SHALL 为知识发布/停用、规则变更、模型配置及启用/测试、索引切换、对话详情查看记录当前主体、动作、对象、结果和时间，复用既有审计保留及脱敏规则。审计日志 SHALL 不保存模型凭据、令牌或完整买家对话。

#### Scenario: Owner changes a model API key
- **WHEN** 店主成功替换或清除模型凭据
- **THEN** 审计可追溯操作者、配置版本和结果，不包含旧 Key 或新 Key

#### Scenario: Staff views a buyer conversation
- **WHEN** 有 AI 权限的店员查看买家对话详情
- **THEN** 记录该查看动作及对象标识，审计本身不复制对话正文
