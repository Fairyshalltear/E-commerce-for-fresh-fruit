# Spec Delta

## Purpose

使管理员能够维护明确可测试的敏感词和拦截规则，并对输入、流式输出、外部模型数据及业务查询执行统一边界，防止规则或提示词绕过身份授权及产生未经允许的业务操作。

## ADDED Requirements

### Requirement: Administrators manage deterministic interception rules
系统 SHALL 支持敏感词及关键词组合规则的新增、编辑、启停、分页和测试，明确规则范围为输入或输出、匹配方式、优先级及拦截提示。首版 SHALL 只支持有限的文字和关键词匹配，不执行管理员提交的任意代码或无界正则；规则发布后新请求使用新版本，当前请求固定开始时的版本。

#### Scenario: Administrator publishes an input rule
- **WHEN** 管理员启用一个输入敏感词规则并提交测试文本
- **THEN** 测试展示命中规则及提示，之后包含该词的新问题在外部调用前被拦截

#### Scenario: Administrator submits an invalid matching expression
- **WHEN** 规则包含不支持的表达式或超过规则长度限制
- **THEN** 系统拒绝保存并说明支持的匹配形式，既有规则保持有效

### Requirement: Streaming output is checked before unsafe text is shown
系统 SHALL 在输出至用户前检查启用的输出敏感词，支持跨供应商片段及跨传输边界的匹配。命中时 SHALL 停止后续生成，显示拦截状态及固定提示；原始命中内容不得进入用户界面或可查看历史。输出过滤只能保证已配置规则匹配，不得宣称可识别所有不当表达。

#### Scenario: Sensitive text is split across chunks
- **WHEN** 被禁止的词语前后部分分别出现在两个流式片段中
- **THEN** 系统仍然识别并拦截，不先显示该词的完整内容

#### Scenario: Output is blocked after safe content has been shown
- **WHEN** 一部分安全文本已显示，随后命中输出规则
- **THEN** 保留安全片段并将回答标记为拦截，停止输出命中内容，不能把该回答计为成功完成

### Requirement: Business tools are read-only and identity-bound
系统 SHALL 仅开放预定义的商品、知识、本人订单/物流及售后/退款查询工具，校验全部参数及业务对象归属。模型、用户输入、模板及知识文档 SHALL 不能指定任意 SQL、任意 URL、他人 buyerId，或触发下单、支付、退款、库存及账户变更。

#### Scenario: Retrieved text instructs a tool to refund automatically
- **WHEN** 知识内容或模型意图要求执行退款或查询所有用户订单
- **THEN** 该指令不能调用未授权工具，服务只允许当前身份的预定义只读查询

#### Scenario: Structured model output supplies a different buyer identity
- **WHEN** 模型结果携带与认证主体不同的用户标识
- **THEN** 系统拒绝该参数并使用服务端认证身份执行归属校验，不返回他人数据

### Requirement: Only necessary information reaches external models
系统 SHALL 将外部模型输入限制为本轮问题、必要的已完成上下文、有效知识摘要及最小业务事实，排除账户凭据、电话号码、详细地址、付款凭据和内部审计信息。用户自由文本中的可识别电话/详细地址等信息 SHALL 尽可能脱敏，入口 SHALL 提示在线模型会处理咨询内容；脱敏不得被表述为绝对匿名保证。

#### Scenario: Buyer asks about an order containing an address
- **WHEN** 订单记录有收货电话与详细地址，而问题仅需要查询物流
- **THEN** 外部模型上下文仅包含必要的状态和物流事实，不包含电话和详细地址

### Requirement: Model endpoint configuration prevents unintended credential forwarding
系统 SHALL 仅接受符合部署策略的模型服务地址，拒绝带用户信息、危险协议、内部服务/元数据地址及未授权重定向；凭据 SHALL 只发送到验证过的目标。测试和正式调用 SHALL 使用同一地址校验规则。

#### Scenario: Model endpoint redirects to a different destination
- **WHEN** 已配置接口将请求重定向到未验证主机
- **THEN** 系统不携带 API Key 跟随该重定向，返回脱敏的连接失败

### Requirement: Rule hits and denied actions have safe audit records
系统 SHALL 保存规则标识、版本、请求、时间及命中阶段等排查信息，敏感命中文本只保留脱敏摘要。知识发布/停用、规则变更、模型配置/启用和记录查看 SHALL 按既有审计机制记录主体与对象，不记录 Key、JWT 或完整会话正文。

#### Scenario: Administrator reviews an intercepted question
- **WHEN** 有权限管理员查看一条敏感词拦截记录
- **THEN** 可见拦截阶段、规则和脱敏摘要，不可见被过滤的原始敏感输出或凭据
