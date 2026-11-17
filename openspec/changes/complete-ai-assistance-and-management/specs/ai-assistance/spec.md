# Spec Delta

## Purpose

为商城访客及已登录买家提供连续、可追溯的水果咨询、商品推荐和本人订单售后查询体验，让对话状态、身份边界及失败后的恢复方式在窗口与历史页面中保持一致。

## ADDED Requirements

### Requirement: Storefront provides an accessible AI conversation entry
系统 SHALL 在商城及买家页面提供悬浮客服入口，在商品详情提供携带当前商品的咨询入口，在订单/售后详情提供携带本人业务对象的咨询入口。桌面窗口和手机界面 SHALL 支持输入、发送、新会话、关闭及查看回答来源，关闭窗口不得取消正在进行的已接受请求。

#### Scenario: Buyer consults from a product page
- **WHEN** 买家从一个商品详情点击咨询并询问“它怎么保存”
- **THEN** 对话使用该商品作为上下文，展示商品名称及有依据的储存说明

#### Scenario: Visitor opens the window on a narrow screen
- **WHEN** 访客在 390px 宽度屏幕打开悬浮客服
- **THEN** 输入框、关闭和发送按钮均可操作，消息与卡片不产生横向页面溢出

### Requirement: Public conversations and buyer conversations remain isolated
系统 SHALL 允许访客咨询公共商品、知识、政策和推荐；访客查询订单或退款时 SHALL 提示买家登录。访客会话仅在当前浏览器会话及有限有效期内可续聊，不提供跨设备历史；登录、退出、账户切换不得自动把前一个身份的会话带入新身份。

#### Scenario: Visitor asks about an order
- **WHEN** 未登录访客询问“我的订单到哪了”
- **THEN** 系统要求登录，不查询或返回任何买家订单

#### Scenario: Browser changes the signed-in buyer
- **WHEN** 买家 A 退出并登录买家 B
- **THEN** 窗口清除 A 的上下文与消息，B 的历史列表和新请求不能访问 A 的会话

### Requirement: Multi-turn context resolves references without guessing
系统 SHALL 保存会话内的商品、已选择的本人订单/售后对象、预算和用途等上下文，支持连续追问；多候选或引用不明确时 SHALL 要求用户选择或澄清。涉及当前价格、库存、订单和退款状态的追问 SHALL 重新读取当前业务数据。

#### Scenario: Buyer follows up on a selected order
- **WHEN** 买家选择订单后继续问“那退款呢”
- **THEN** 系统查询该订单的本人售后记录；存在多个售后申请时提供选择

#### Scenario: Reference is ambiguous
- **WHEN** 会话中有多个候选商品，用户询问“这个甜吗”且未指定商品
- **THEN** 系统请求选择商品，不能自行套用某一个商品的甜度

### Requirement: Buyer-specific business answers use authorized current records
系统 SHALL 仅查询当前登录买家的订单、已登记物流、售后与退款记录，并展示实际状态及查询时间。查询他人或不存在的业务对象 SHALL 返回一致的不可查询结果。模拟物流和退款 SHALL 明确标记；没有节点或退款凭证时不得宣称已送达、已到账或承诺具体时间。

#### Scenario: Buyer has several recent orders
- **WHEN** 买家询问“我的订单到哪了”且有多个可选订单
- **THEN** 系统展示本人订单选择卡片，选择后返回该订单状态及最近登记的物流节点

#### Scenario: Buyer submits another buyer's order number
- **WHEN** 买家提供不属于自己的订单号或订单标识
- **THEN** 系统不返回订单存在性、状态、商品、地址、电话或物流信息

#### Scenario: Refund is recorded as simulated success
- **WHEN** 本人退款记录为模拟退款成功
- **THEN** 回答说明系统已完成模拟退款，并展示实际退款金额和记录时间，不宣称银行到账

### Requirement: After-sales consultation reflects implemented policy and workflow
系统 SHALL 依据已发布政策解释售后规则，并用当前业务状态判断可提供的操作入口。系统 SHALL 准确说明待发货退款、确认收货后七天内申请退货退款等当前规则；不得承诺未提供的换货流程或由对话执行审批、退款及状态变更。

#### Scenario: Buyer asks whether a completed order can be returned
- **WHEN** 买家查询一个确认收货超过七天的订单能否退货
- **THEN** 系统说明当前系统的申请时间限制，不返回可以直接提交的承诺

#### Scenario: Buyer asks AI to approve a refund
- **WHEN** 买家要求“直接给我退款”
- **THEN** 系统说明申请及审核流程并提供适用的站内入口，业务记录保持原状态

### Requirement: Recommendations are grounded in current purchasable products
系统 SHALL 按明确的预算、口味、应季月份、礼盒和用途筛选当前上架、分类可用且有可售规格的商品，并显示真实商品/规格/价格、推荐理由及详情入口。无匹配时 SHALL 说明缺少匹配商品并询问是否调整条件，未经用户同意不得放宽预算或礼盒等硬条件。

#### Scenario: Buyer requests a seasonal gift box within budget
- **WHEN** 用户询问本月适合送长辈的水果礼盒并指定预算
- **THEN** 返回同时满足礼盒、月份、预算和可售条件的商品，理由依据已维护资料

#### Scenario: Matching products have sold out
- **WHEN** 满足用户全部条件的商品均没有可售规格
- **THEN** 系统说明暂无符合条件的可售商品，不编造商品或推荐已下架商品

### Requirement: Streamed turns have explicit completion and interruption states
系统 SHALL 逐步输出可安全显示的回答，区分处理中、完成、主动停止、失败与拦截状态；每轮仅有一个终态。停止及连接中断 SHALL 停止后续生成并保存已产生文本为未完成回答，不得将其作为完整答案或下一轮事实依据。窗口重新打开 SHALL 能查询该轮状态。

#### Scenario: Buyer stops a streamed answer
- **WHEN** 买家在回答生成中点击停止
- **THEN** 不再生成后续内容，当前回答标记为已停止且保留已经显示的文本

#### Scenario: Connection drops during a turn
- **WHEN** 已接受的流式请求在生成中断开连接
- **THEN** 请求进入终态并释放生成占用，用户重新打开会话可见真实结果及可用的重试操作

### Requirement: Duplicate submissions and retries preserve message identity
系统 SHALL 对同一身份、会话和请求标识的重复发送保持幂等，不重复创建提问、计数或发起模型调用。同一会话存在生成中请求时 SHALL 拒绝另一个新提问并提示等待/停止。用户主动重试 SHALL 创建关联原提问的新回答尝试，旧回答状态可追溯。

#### Scenario: Sending is retried after the acceptance response is lost
- **WHEN** 客户端用相同请求标识重发已接受的问题
- **THEN** 系统返回原请求状态，不新增提问或额外模型调用

#### Scenario: Buyer explicitly retries a failed response
- **WHEN** 买家对失败回答执行重试
- **THEN** 创建一个新的回答尝试并保留原失败记录，该操作不增加唯一提问量

### Requirement: Buyer can review and delete retained conversations
系统 SHALL 为买家提供按更新时间分页的本人会话列表、消息详情、继续提问及删除功能。买家会话默认保留 180 天，删除或过期后 SHALL 清除可访问的文本、引用和上下文；匿名汇总计数可以保留，但不得从统计恢复被删除的对话。访客会话有效期 SHALL 不超过 24 小时。

#### Scenario: Buyer resumes a prior conversation on another device
- **WHEN** 买家在另一设备登录并选择未过期的本人历史会话
- **THEN** 系统显示原消息顺序和状态，并支持使用已确认上下文继续提问

#### Scenario: Buyer deletes a conversation while a turn is active
- **WHEN** 买家确认删除包含生成中请求的会话
- **THEN** 系统停止生成并清除该会话内容，迟到的生成结果不能恢复已删除的会话

### Requirement: Completed answers accept owned feedback
系统 SHALL 允许回答所属身份对已完成的正常或降级答案选择“有帮助/无帮助”及可选原因，支持修改且每个回答仅保留一个当前有效评价。生成中、已停止、失败和拦截结果 SHALL 不可评价；用户不能评价他人的回答。

#### Scenario: Buyer changes feedback
- **WHEN** 买家将本人回答的评价从无帮助改为有帮助
- **THEN** 更新该回答的单条有效评价，统计不重复累加评价人数或回答数
