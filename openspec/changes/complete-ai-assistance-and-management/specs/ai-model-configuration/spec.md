# Spec Delta

## Purpose

提供可替换的在线生成与向量服务配置，使店主能够安全接入模型、验证能力、调整客服提示词及调用参数，并在凭据、配置或外部服务异常时保持商城与基础咨询可用。

## ADDED Requirements

### Requirement: Owner configures independent generation and embedding endpoints
系统 SHALL 支持配置生成与向量服务的供应商、接口地址、模型名称和各自凭据，可选择安全复用同一凭据。配置 SHALL 展示启用状态、版本和更新时间，不强制某个固定供应商。首次安装未配置模型时 SHALL 允许应用启动并提供基础咨询模式。

#### Scenario: Owner configures the recommended supplier
- **WHEN** 店主填写百炼工作空间对应的接口、生成模型和向量模型并保存
- **THEN** 保存独立可验证的服务配置，页面显示脱敏状态及配置版本

#### Scenario: Application starts without model credentials
- **WHEN** AI 配置为空或关闭，其他商城必需配置有效
- **THEN** 商城正常启动，AI 入口说明基础模式及可用能力

### Requirement: Model credentials remain write-only and encrypted
系统 SHALL 加密持久化模型 API Key，读取配置仅返回是否已配置及脱敏标识，日志、错误和前端持久缓存不得包含明文 Key。编辑时空值 SHALL 表示保留原凭据，清除 SHALL 使用独立明确动作；缺少有效加密主密钥时 SHALL 拒绝保存凭据，并使受影响在线服务降级而不泄露密钥。

#### Scenario: Owner reads and edits existing configuration
- **WHEN** 店主读取已有配置并仅修改模型名，不提交新 Key
- **THEN** 原凭据继续使用，响应与页面均不包含原 Key 明文

#### Scenario: Encryption configuration is absent
- **WHEN** 店主在未配置有效加密主密钥时尝试保存 API Key
- **THEN** 返回清晰配置错误且不写入明文或无效凭据

### Requirement: Configuration tests verify required capabilities safely
系统 SHALL 提供普通生成、流式输出、结构化意图结果及向量接口的独立测试状态，显示成功/失败、响应时长和脱敏原因。测试 SHALL 使用无买家信息的固定内容，不改变当前启用版本；测试结果不得把 HTTP 成功但无有效结果、向量维度错误或中途流失败标为通过。

#### Scenario: Generation succeeds but embeddings fail
- **WHEN** 测试中生成和流式输出可用，向量接口返回错误
- **THEN** 页面分别显示能力状态，不宣称完整 RAG 接入成功

#### Scenario: Owner tests an unsaved draft
- **WHEN** 店主测试尚未启用的模型配置
- **THEN** 测试只使用该草稿，线上会话仍使用原配置版本

### Requirement: Prompt and generation parameters are versioned and validated
系统 SHALL 支持可预览的客服提示词模板、温度、输出长度、上下文轮数和超时等参数，并校验范围及允许的模板变量。管理员可编辑的模板 SHALL 不得替换身份归属、安全规则、只读工具和证据约束。单轮已接受请求 SHALL 固定使用其开始时的配置版本。

#### Scenario: Owner updates a prompt while an answer is streaming
- **WHEN** 店主发布新提示词版本且已有请求仍在生成
- **THEN** 该请求使用原配置完成，新请求使用新配置；记录可区分两者

#### Scenario: Template requests bypassing ownership checks
- **WHEN** 模板内容要求忽略买家归属或执行退款
- **THEN** 实际服务仍执行归属和只读工具限制，模板不能扩大权限

### Requirement: Embedding changes do not mix incompatible indices
系统 SHALL 在向量供应商、模型或维度改变时构建新的完整索引版本并显示进度，在切换前保持原兼容索引或可靠关键词模式。全部有效知识成功建立新索引后 SHALL 原子切换；失败、取消或文档状态变化不得混合新旧维度或恢复失效文档。

#### Scenario: Owner changes embedding dimensions
- **WHEN** 店主将向量配置从一个维度切换到另一个维度
- **THEN** 系统构建并校验新索引，查询只使用与其配置兼容的完整索引，不对不同维度计算相似度

### Requirement: External calls are bounded and recoverable
系统 SHALL 限制问题长度、上下文长度、每轮调用次数、并发量、每身份请求频率及每日调用额度，并设置连接、首内容、空闲和整轮超时。超过限额 SHALL 清晰提示；在线调用不可用时按基础降级规范处理。已经产生输出的生成请求不得自动重新调用并重复输出。

#### Scenario: Visitor exceeds the request frequency limit
- **WHEN** 访客在限制窗口内发送超过允许数量的问题
- **THEN** 系统返回限流结果和可重试时间，不发起新的模型调用

#### Scenario: External model never sends answer content
- **WHEN** 供应商已建立连接但在首内容超时内未返回有效回答
- **THEN** 结束该调用并执行适用的降级或失败路径，不让会话无限处于生成中

### Requirement: Owner deletes configurations with reference protection
系统 SHALL 提供仅店主可用的配置删除入口及接口。未被回答或索引引用的版本 SHALL 永久删除；历史回答或已停用/淘汰索引引用的版本 SHALL 从模型管理列表隐藏并保留追溯数据。当前激活配置、仍有 ACCEPTED/RUNNING 回答的版本、BUILDING/READY/ACTIVE 向量代次或 PENDING/RUNNING 索引任务所使用的版本 SHALL 拒绝删除并提示先关闭、停止、取消构建或切换代次。删除检查 SHALL 与请求接受、激活及索引构建并发安全；已隐藏配置不得通过详情、继承草稿、测试、激活或构建接口重新使用。删除及隐藏 SHALL 留审计，页面确认显示真实删除语义并清理已删除配置的编辑与测试状态。

#### Scenario: Owner deletes an unused draft
- **WHEN** 店主确认删除没有回答或索引引用的草稿
- **THEN** 配置被永久移除，页面刷新列表并清除原配置状态，直接读取返回不存在

#### Scenario: Owner deletes a historically referenced version
- **WHEN** 非激活配置只被已完成回答或已淘汰索引引用且店主确认删除
- **THEN** 配置从管理列表隐藏，原回答的模型版本和名称仍可追溯，隐藏版本不能重新激活或测试

#### Scenario: Configuration is still serving an answer or index
- **WHEN** 店主尝试删除当前激活、仍有处理中回答或仍被运行中索引使用的配置
- **THEN** 返回冲突并说明解除占用的方法，配置、回答及索引保持有效
