# Spec Delta

## Purpose

让管理员维护可追溯的商品知识、水果知识、常见问题和售后政策，并使问答只使用已发布且仍有效的依据，知识更新、检索故障及模型不可用时仍保持可信的回答边界。

## ADDED Requirements

### Requirement: Knowledge editing and publication have separate states
系统 SHALL 支持水果知识、商品知识、FAQ 和售后政策的新增、编辑、预览、发布、停用及分页筛选；草稿编辑不得覆盖当前发布版本。发布 SHALL 校验标题、类型、内容及可选商品关联，并为内容生成不可变版本标识。FAQ SHALL 支持标准问题、同义问法及标准答案。

#### Scenario: Administrator edits a published policy
- **WHEN** 管理员修改已发布政策但尚未发布新版本
- **THEN** 买家问答继续使用原发布版本，不检索新草稿

#### Scenario: Administrator publishes a FAQ
- **WHEN** 管理员发布具有标准问题、同义问法和答案的 FAQ
- **THEN** 后续问答可以检索该 FAQ，配置的同义问法可用于确定性匹配

### Requirement: Publication and indexing use visible version-consistent states
系统 SHALL 展示待索引、索引中、就绪及失败状态和可重试错误。新版本索引完成前 SHALL 保持原版本服务；索引成功后只允许新版本生效。停用 SHALL 立即阻止文档的所有版本参与新检索；迟到的索引任务不得重新启用已停用或较旧内容。

#### Scenario: A new revision fails to index
- **WHEN** 发布的新修订因向量服务故障而索引失败
- **THEN** 页面显示失败及重试入口，已有有效版本仍可回答，新草稿不混入该版本

#### Scenario: Administrator disables a document while indexing
- **WHEN** 管理员在索引任务完成前停用文档
- **THEN** 任务完成也不能使该文档再次参与问答

### Requirement: Product knowledge follows catalog availability and revision
系统 SHALL 从商品的当前资料生成关联知识，并在资料编辑、上架、下架、删除及分类可用性变化后同步失效状态或更新索引。尚未完成更新的旧商品知识 SHALL 不得提供过期事实；当前价格与库存 SHALL 始终读取商品业务数据。手工商品补充知识不得覆盖关联商品的结构化事实。

#### Scenario: Product storage instructions change
- **WHEN** 管理员更新商品储存说明且新索引尚未完成
- **THEN** 咨询可以使用当前商品资料直接回答，不使用旧知识中的储存说明

#### Scenario: Product is unlisted after being indexed
- **WHEN** 商品下架或所属分类停用
- **THEN** 新公共问答和推荐不引用其当前商品知识；历史订单仍可查询已购商品快照

### Requirement: Retrieval combines relevant evidence and exposes test results
系统 SHALL 检索当前有效的知识及商品资料，支持精确 FAQ、关键词与语义匹配，并为管理员提供检索测试，展示命中文档、版本、摘要及排序信息。知识查询不得加入买家私人订单或历史对话作为公共知识。

#### Scenario: Administrator tests a preservation question
- **WHEN** 管理员输入“草莓怎么保存”执行检索测试
- **THEN** 显示有效的相关文档及版本，草稿和停用文档不出现在结果中

#### Scenario: Query vectors do not match the active index profile
- **WHEN** 当前查询向量模型或维度与已建索引不一致
- **THEN** 系统不进行不兼容语义比较，使用仍可用的关键词/FAQ 检索并标明检索降级

### Requirement: Answers preserve evidence identity and acknowledge missing facts
系统 SHALL 为知识型回答提供服务端验证过的来源标题、版本及支持该回答的摘要，为业务型回答提供实际业务卡片。生成内容不得把无法找到依据的产地、甜度、保存期限、配送承诺或政策表述为确定事实。未知问题 SHALL 明确说明缺少可靠依据并提供适用的商品/政策查看入口。

#### Scenario: Product has no recorded sweetness
- **WHEN** 用户询问某商品的具体糖度，但商品及已发布知识未提供测量数据
- **THEN** 回答说明暂无具体数据，不编造糖度数值或把口感描述当作测量值

#### Scenario: User opens a historical answer after knowledge changes
- **WHEN** 用户查看使用旧知识版本完成的未过期历史回答
- **THEN** 保留当时依据的标题、版本和摘要，并说明它属于历史回答；新提问使用当前有效知识

### Requirement: Degraded service retains deterministic useful answers
系统 SHALL 在模型未配置、关闭、超时、限额或不可用时明确标记基础回答。基础模式 SHALL 支持已发布标准 FAQ 的可靠匹配、明确商品的已维护事实、本人订单/物流/售后/退款查询及结构化条件推荐；不能可靠识别的多轮表达 SHALL 要求选择对象、补充条件或查看站内页面，不伪装为模型回答。

#### Scenario: LLM is unavailable but the selected order can be queried
- **WHEN** 模型调用失败且已登录买家已选定本人订单
- **THEN** 返回从业务数据组织的订单/物流答案并说明当前使用基础服务

#### Scenario: Both generative and vector endpoints are unavailable
- **WHEN** 模型和向量接口均不可用，用户命中已发布 FAQ 的标准问题或同义问法
- **THEN** 返回标准答案和来源，不依赖向量服务或在线生成

#### Scenario: Failure happens after some answer text has been delivered
- **WHEN** 模型已输出部分回答后发生错误
- **THEN** 原尝试标记失败，不将基础答案拼接为原完整答案；用户可重试获得独立且明确标记的基础回答

### Requirement: Published policy does not bypass business eligibility
系统 SHALL 在政策知识与实际操作条件不一致时展示真实业务限制及政策说明，不允许知识编辑改变订单或售后的服务端资格校验。管理员 SHALL 可以在检索/预览中发现与现有售后申请条件相关的冲突提示。

#### Scenario: Published text promises a longer return window
- **WHEN** 政策文本描述超过当前七天业务申请窗口，用户查询是否可以申请
- **THEN** 系统说明实际申请条件，不承诺可以绕过业务校验；管理侧预览提示需协调政策与系统规则
