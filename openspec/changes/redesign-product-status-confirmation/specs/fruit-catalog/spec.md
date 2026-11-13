# Spec Delta

## Purpose

为管理员提供清晰、可核对的商品发布状态确认交互，衔接商品维护与商城可见性规则。通过独立弹窗、明确操作按钮、提交反馈及取消行为，让管理员在手机和桌面上可靠地完成上下架。

## ADDED Requirements

### Requirement: Administrator reviews product status changes in an isolated modal
系统 SHALL 在管理员点击商品上架或下架时显示位于可视区域中央的独立确认弹窗，以不透明卡片承载内容，并使用覆盖页面的半透明遮罩阻止操作底层列表。弹窗 SHALL 显示商品名称、主图或占位、分类、当前状态、目标状态及操作影响；打开弹窗本身 SHALL 不提交状态修改。

#### Scenario: Administrator opens publication confirmation
- **WHEN** 管理员点击已下架商品“珠海橙子”的上架操作
- **THEN** 弹窗显示“上架商品”、该商品摘要、“已下架 → 已上架”及“上架后，商品将在商城展示；库存为 0 时显示售罄”，并提供“取消”和“确认上架”，未发生状态修改请求

#### Scenario: Administrator opens unlisting confirmation
- **WHEN** 管理员点击已上架商品的下架操作
- **THEN** 弹窗显示“下架商品”、该商品摘要、“已上架 → 已下架”及“下架后，商品将从商城推荐、列表和详情中隐藏，已有收藏会显示商品不可用”，并提供“取消”和“确认下架”

### Requirement: Cancellation leaves product status unchanged
系统 SHALL 在空闲弹窗中支持取消按钮、关闭按钮及 Escape 关闭，并保持商品状态及当前筛选和页码。点击遮罩 SHALL 不关闭弹窗或提交修改。初始焦点 SHALL 位于取消按钮，Tab 与 Shift+Tab SHALL 在弹窗可操作元素内移动；关闭后 SHALL 将焦点返回触发操作或仍可用的列表控件。

#### Scenario: Administrator cancels before submission
- **WHEN** 管理员点击取消、关闭按钮或按 Escape
- **THEN** 弹窗关闭，没有商品状态修改请求，列表筛选及页码保持不变，焦点回到列表

#### Scenario: Administrator uses keyboard or clicks the backdrop
- **WHEN** 管理员打开弹窗后通过 Tab 导航或点击遮罩
- **THEN** 键盘焦点留在弹窗内，遮罩点击保持弹窗打开；只有明确激活确认按钮才提交修改

### Requirement: Product status submissions report progress and results
系统 SHALL 仅在明确确认后提交目标状态，携带打开弹窗时的商品版本。请求未完成时 SHALL 显示“上架中…”或“下架中…”，阻止重复请求并禁用取消、关闭及 Escape。修改成功 SHALL 关闭弹窗、报告“商品已上架”或“商品已下架”，并按当前查询条件刷新列表；操作成功后的列表刷新失败 SHALL 仍明确报告状态修改已成功并提供列表重试。

#### Scenario: Administrator confirms repeatedly during a slow request
- **WHEN** 管理员确认上架后再次点击确认或尝试关闭仍在提交的弹窗
- **THEN** 只有一次状态修改请求，按钮显示上架中，弹窗保持打开直至请求结束

#### Scenario: Status update succeeds and list refresh fails
- **WHEN** 状态接口返回成功而随后列表查询失败
- **THEN** 弹窗关闭，页面保留上架或下架成功提示，并显示列表加载错误与重试入口，不把成功修改描述为操作失败

#### Scenario: Product leaves the active status filter
- **WHEN** 管理员在“已上架”筛选下成功下架某商品
- **THEN** 刷新后的列表不再显示该商品，保留筛选条件，按现有分页规则处理空页

### Requirement: Failed product status changes remain reviewable
系统 SHALL 在修改失败时保留弹窗、商品摘要及目标状态，在弹窗内显示可理解的错误，并恢复关闭和取消操作。上架条件不足 SHALL 显示服务端原因。版本冲突 SHALL 禁止直接重试旧版本，提供“重新读取”；读取成功 SHALL 更新摘要和版本并要求再次明确确认，若商品已达到目标状态则 SHALL 关闭弹窗并刷新列表。商品已删除或无法读取时 SHALL 提示对象不可用并禁止确认。

#### Scenario: Publication requirements are not met
- **WHEN** 上架请求因分类不可用、缺少图片或无启用规格被拒绝
- **THEN** 弹窗保持打开并显示具体原因，商品状态不变，管理员可取消后编辑商品

#### Scenario: A stale product version is submitted
- **WHEN** 另一管理员已更新商品，当前弹窗的确认请求返回版本冲突
- **THEN** 弹窗提示重新读取并禁止继续使用旧版本提交；读取新版本后展示最新摘要，管理员重新确认才发起新的修改

#### Scenario: Reloaded product already has the target status
- **WHEN** 重新读取发现商品已经是本次目标状态
- **THEN** 弹窗关闭并提示当前状态已经更新，刷新列表，不额外提交相同状态

#### Scenario: Network request fails
- **WHEN** 状态接口发生网络错误或超时，前端不能确定是否已提交成功
- **THEN** 弹窗显示请求结果未确认并要求重新读取状态，未读取前禁止直接重复提交

### Requirement: Product status confirmation remains usable across screen sizes
系统 SHALL 在至少 320、390 和 1280 像素宽度下完整呈现上下架弹窗，商品名称允许换行，图片缺失使用占位，按钮和关闭图标不重叠。弹窗 SHALL 提供可识别的标题、关闭按钮标签及可被辅助技术感知的错误和提交反馈；高度不足时 SHALL 保持确认与取消操作可到达。现有商品删除、分类操作及商品编辑重新读取确认框 SHALL 保持正常的遮罩、图标和按钮显示。

#### Scenario: Long name is displayed on a narrow screen
- **WHEN** 管理员在 320 像素宽度屏幕为名称达到 100 字符的商品打开确认弹窗
- **THEN** 商品名称换行而不覆盖关闭图标，按钮可操作，弹窗不导致页面横向溢出

#### Scenario: Another administration confirmation is opened
- **WHEN** 管理员打开商品删除、分类停用或删除、商品编辑重新读取确认框
- **THEN** 遮罩覆盖页面、弹窗内容独立于列表、关闭图标和按钮具有完整样式，原有确认与取消行为仍可用
