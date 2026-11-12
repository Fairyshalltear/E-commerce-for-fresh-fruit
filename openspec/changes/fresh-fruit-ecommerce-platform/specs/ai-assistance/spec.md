# Spec Delta

## Purpose

为买家提供水果知识、商品、订单物流和售后政策的对话式查询入口，并让管理员维护回答依据和检查问答效果，确保答辩演示不依赖在线模型服务。

## ADDED Requirements

### Requirement: AI answers from locally managed knowledge
系统 SHALL 使用预先准备在本地的中文 embedding 模型，将水果知识、商品信息、常见问题和售后政策与买家问题进行语义检索，并以基于检索结果的模板回复。系统 SHALL 不要求在线大模型 API 或运行时网络访问；无足够匹配内容时 SHALL 明确提示未找到可靠答案，不得编造政策或商品事实。

#### Scenario: Local semantic retrieval finds relevant knowledge
- **WHEN** 买家询问一个知识库中有依据的问题
- **THEN** 系统检索相关内容并返回对应模板回复及相关商品或知识提示

#### Scenario: No relevant knowledge is found
- **WHEN** 买家问题未能检索到足够相关的知识
- **THEN** 系统说明当前无法可靠回答并提供人工查看商品或联系管理员的引导

### Requirement: Buyer has multi-turn AI conversations
系统 SHALL 提供前台 AI 客服入口，保存买家的会话与消息历史，并允许买家查看历史对话及在同一会话中继续提问。

#### Scenario: Buyer continues a prior conversation
- **WHEN** 买家在同一会话中发送后续问题
- **THEN** 系统保留该会话上下文和消息顺序，并返回本轮回复

### Requirement: AI can answer buyer-specific order questions privately
系统 SHALL 允许已登录买家通过 AI 查询其本人订单状态和已登记物流节点，并 SHALL 对访客或其他买家的订单信息拒绝访问。商品咨询、售后政策咨询和基于当前上架商品的基础推荐也 SHALL 可通过问答入口使用。

#### Scenario: Buyer asks about own shipment
- **WHEN** 已登录买家询问其本人订单的物流进度
- **THEN** 系统读取该买家订单及模拟物流记录并以模板返回当前状态

#### Scenario: Buyer asks about another account order
- **WHEN** 买家尝试通过 AI 查询不属于自己的订单
- **THEN** 系统不返回该订单的状态、地址或物流信息

### Requirement: Administrator maintains AI knowledge and reviews conversations
系统 SHALL 允许管理员新增、编辑、发布和停用水果知识、FAQ 与售后政策，并查看问答记录、问题量、高频问题和买家反馈统计。知识内容变化后，系统 SHALL 更新对应的本地 embedding 检索数据。管理员 SHALL 能维护基础敏感词拦截规则；本版本不提供在线大模型供应商或 API 密钥管理。

#### Scenario: Administrator publishes updated knowledge
- **WHEN** 管理员发布新增或修改后的知识内容
- **THEN** 后续问题检索使用该内容及其更新后的向量表示

#### Scenario: Administrator reviews question statistics
- **WHEN** 管理员打开 AI 问答管理页
- **THEN** 系统展示问答记录及按时间范围汇总的问题量、高频问题和反馈情况
