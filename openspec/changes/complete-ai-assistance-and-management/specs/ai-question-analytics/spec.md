# Spec Delta

## Purpose

为管理员提供有权限约束的脱敏问答查看及可复核的使用效果统计，使问题数量、高频问题、回答结果和用户满意度来自真实记录，并在用户删除对话后保持内容隐私与匿名汇总的一致。

## ADDED Requirements

### Requirement: Authorized administrators review searchable conversation records
系统 SHALL 允许有 AI 权限的管理员按时间、买家标识/访客、场景、结果、关键词及反馈筛选分页记录，查看消息顺序、回答尝试、引用版本、模型版本、响应耗时及降级/拦截原因。正文及查询条件 SHALL 脱敏；无权限、已删除或超过内容保留期的记录不得返回对话文本。

#### Scenario: Administrator inspects a failed conversation
- **WHEN** 有 AI 权限的店员筛选失败记录并打开详情
- **THEN** 展示失败阶段、已保留的安全文本及脱敏原因，不能看到 API Key 或被拦截原文

#### Scenario: Buyer deletes a previously viewed conversation
- **WHEN** 买家删除该会话，管理员再次请求详情
- **THEN** 不再返回该会话文本、上下文和引用摘要，匿名计数不提供还原入口

### Requirement: Question volume counts unique accepted user questions
系统 SHALL 按北京时间和指定日期区间统计唯一接受的用户提问，包含进入规则拦截流程的提问；请求格式错误、认证失败和限流拒绝不增加提问量。同一请求标识重复提交和对原问题的主动回答重试 SHALL 不增加唯一提问量；重试次数 SHALL 单独展示。

#### Scenario: A duplicate send and an explicit retry occur
- **WHEN** 用户发送一个问题、重复提交相同请求标识并主动重试一次回答
- **THEN** 提问量为一，回答尝试数和重试数按实际发生分别记录

### Requirement: Answer outcomes and latency have defined denominators
系统 SHALL 分别统计正常完成、基础降级完成、失败、停止和拦截的回答尝试，且每次尝试只进入一个终态。回答成功率 SHALL 为正常完成及降级完成尝试数除以全部终态尝试数；页面 SHALL 同时展示降级占比、尝试数、处理中数和样本量。首内容时间与总耗时 SHALL 使用真实时间记录，缺失值不得伪造为零。

#### Scenario: One answer succeeds and another is stopped
- **WHEN** 区间内有一条完成、一条停止及一条仍在生成的尝试
- **THEN** 终态尝试数为二，成功率为百分之五十，处理中数为一

### Requirement: Frequent questions avoid exposing private text
系统 SHALL 以命中的 FAQ、确定的场景/主题或脱敏规范化公共问题分组展示高频问题及数量，不显示私人订单号、售后单号、手机号或地址。归组不得让模型自由生成包含买家信息的统计标题；无法安全归组的问题 SHALL 计入其他问题。

#### Scenario: Different buyers ask about their shipment
- **WHEN** 多个买家使用不同订单号询问物流
- **THEN** 高频问题可归为订单物流查询，列表不暴露具体单号或个人信息

### Requirement: Satisfaction is based on current valid answer feedback
系统 SHALL 按回答尝试创建时间所属区间统计当前有效反馈，满意度为有帮助数量除以有帮助及无帮助数量之和；未评价回答不进入分母。页面 SHALL 显示有帮助数、无帮助数、评价总数和评价覆盖率；无反馈时满意度 SHALL 显示暂无评价而非百分之百或零。

#### Scenario: Most answers have not been rated
- **WHEN** 十条完成回答中只有一条有帮助和一条无帮助
- **THEN** 满意度为百分之五十，评价数量为二，评价覆盖率为百分之二十

#### Scenario: User updates feedback
- **WHEN** 用户将无帮助改为有帮助
- **THEN** 按该回答原尝试所属日期重新计算当前有效统计，不产生新增评价

### Requirement: Date filters and empty results remain verifiable
系统 SHALL 支持最近七天、三十天及自定义不超过 180 天的区间，并按北京时间自然日展示趋势、场景分布和上述指标。起始日包含、结束日包含对应的整个自然日；无数据 SHALL 展示零计数、空排行及无可计算比例，筛选无效 SHALL 明确报错。

#### Scenario: Events occur across midnight
- **WHEN** 一个提问在北京时间某日 23:59 创建，回答尝试在次日完成
- **THEN** 问题按问题创建日统计，回答结果按尝试创建日统计，耗时及完成时间按实际值记录

#### Scenario: Selected range has no feedback or questions
- **WHEN** 管理员选择完全没有数据的日期范围
- **THEN** 图表与计数显示真实空结果，成功率和满意度显示无样本

### Requirement: Retention keeps anonymous metrics without restoring deleted content
系统 SHALL 对过期/删除会话清除原始问题、回答、引用、上下文及可识别关联；在统计窗口内可以保留不可还原的场景、终态、耗时及已匿名化反馈计数。删除内容 SHALL 不使过去的唯一提问与回答计数重复增加或凭空消失，匿名化后不允许再修改原反馈。

#### Scenario: A retained buyer conversation expires
- **WHEN** 会话达到 180 天内容保留期限并完成清理
- **THEN** 原内容不能再读取，仍在允许统计窗口内的匿名指标保持可复核且不带买家关联
