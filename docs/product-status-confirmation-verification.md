# 商品上下架确认界面验收记录

变更：`redesign-product-status-confirmation`。验证日期：2026-11-13。

## 环境与验证方式

前端使用现有 Vue 3 / Element Plus，在开发服务及 Vite 生产构建预览中分别验证。Chrome 浏览器由 Playwright 驱动；后端使用现有 Spring Boot 实现、独立 H2 内存数据库（MySQL 模式）和测试 schema，监听 8086；开发验收前端监听 5186，生产构建预览监听 5187。

管理员、买家、分类、图片和商品均为隔离测试数据；没有向日常 8080 服务发送写入请求。后端接口和数据库模型未修改。类型检查与构建在 `front-end` 中执行。

## 校验结果

| 校验 | 结果 |
| --- | --- |
| `npm run typecheck` | 通过 |
| `npm run build` | 通过；官方 Dialog / Button / Icon / MessageBox 样式入口可解析 |
| Chrome 交互验收 | 16 组通过，使用真实上下架与商品读取接口 |
| 生产构建浏览器验收 | 商品页面直接访问及取消初始焦点、图片加载失败占位、390/320px 手机错误态、分类页直接访问的 MessageBox 依赖均通过 |
| 页面运行错误 | 无 JavaScript 页面异常；网络故障用例中预期出现的资源请求失败被单独识别 |
| `git diff --check` | 通过 |
| OpenSpec 严格校验及 apply 状态 | 通过；8/8 项完成，`all_done` |

## 分组验收证据

### 1. 弹窗样式与组件（任务 1.1–1.3）

- 白色卡片宽 460px，遮罩覆盖页面，标题、图片、商品名称、分类、状态转换和说明完整；下架使用暖橙色，上架使用绿色。
- 商品删除、分类停用、分类删除、商品编辑重新读取确认框均显示不透明背景、正常按钮和小尺寸关闭图标。生产构建直接进入分类页也能加载完整样式。
- 上下架在 1280、390、320px 宽度检查通过，卡片两侧至少留 16px，无页面横向溢出。
- 100 字符商品名在 320px 宽度自然换行；无主图及图片加载失败时显示占位。320×300 短屏可滚动到确认和取消按钮。
- 打开后焦点为取消；Tab / Shift+Tab 始终留在弹窗内。Escape、关闭按钮和取消关闭弹窗且不发修改请求；遮罩点击不关闭。关闭后焦点返回触发按钮，触发商品离开列表时返回查询按钮。

### 2. 上下架流程与反馈（任务 2.1–2.3）

- 打开及取消的状态修改请求数为 0。延迟响应时，重复点击仅产生 1 次 PATCH；确认显示“下架中…”，取消、关闭和 Escape 均不能关闭弹窗。
- 修改成功关闭弹窗并显示成功反馈。下架后公开详情返回 404；买家收藏显示下架失效原因。再次上架后详情可读，同一收藏恢复可用。
- 在“已上架”筛选下下架唯一命中商品，列表变空，URL 的状态和关键词保留。13 件下架商品的第 2 页仅有 1 件，将其上架后页码回到有效第 1 页，剩余 12 件，筛选保留。
- PATCH 成功但随后列表查询失败时，页面同时保留“商品已下架”和列表错误；“重新加载”恢复列表，不把已经成功的操作显示为失败。
- 无图片及分类停用时，上架被真实接口拒绝，具体原因显示在弹窗内，原状态保留。
- 两个窗口：先打开状态弹窗，另一窗口编辑名称并保存；旧弹窗确认收到 `VERSION_CONFLICT`，只提供重新读取。读取后显示新名称及版本，再次确认才发送新修改。
- 若另一个窗口已经完成目标状态，重新读取后直接关闭并刷新，没有额外发送相同状态修改。
- 断开状态请求时，弹窗提示结果未确认并禁止直接确认；详情重新读取也失败时继续保留错误和取消入口，恢复读取后才允许重新确认。
- 商品在弹窗打开后被删除，确认收到 404，显示商品不可用并禁用确认；取消仍可用。

### 3. 集成验收（任务 3.1–3.2）

- 上架→下架→再次上架闭环以及商城详情、收藏失效/恢复已通过真实接口与页面验证。
- 开发和生产构建中的直接访问、焦点、样式依赖均通过；商城首页与买家个人资料页可用。
- 手机正常态、错误态、长名称、缺图及短屏截图均已保存。截图中的“界面验收”商品、分类及图片为测试数据。

## 复现步骤

1. 管理员打开商品管理，对下架商品点击上架；核对摘要和目标状态，依次尝试取消、关闭、Escape 和遮罩点击。
2. 再次打开并确认，观察提交中文字；接口成功后检查成功反馈及列表状态。
3. 买家收藏商品，然后后台下架；检查商城隐藏与收藏失效，再上架检查恢复。
4. 打开缺图片商品的上架确认，确认后核对弹窗中的服务端错误与取消入口。
5. 两个浏览器窗口，一个打开上下架弹窗，另一个编辑该商品并保存；第一个确认后核对版本冲突及重新读取的新摘要。
6. 延迟或断开状态接口，核对重复点击、取消和 Escape 的限制；恢复网络后重新读取，再明确确认。
7. 模拟状态修改成功、列表读取失败，核对成功反馈与“重新加载”；在状态筛选和最后一页验证商品离开列表后的结果。
8. 在 1280、390、320px 宽度及 320×300 短屏核对完整操作，回归其他管理确认框。

## 截图

- [下架桌面（生产构建）](screenshots/product-status-confirmation/final-desktop.png)
- [上架桌面](screenshots/product-status-confirmation/publish-1280.png)
- [上架手机 390px](screenshots/product-status-confirmation/publish-390.png) / [320px](screenshots/product-status-confirmation/publish-320.png)
- [下架手机 390px](screenshots/product-status-confirmation/unlist-390.png) / [320px](screenshots/product-status-confirmation/unlist-320.png)
- [100 字符名称与短屏](screenshots/product-status-confirmation/long-name-320x300.png)
- [上架条件失败](screenshots/product-status-confirmation/publication-error.png) / [版本冲突](screenshots/product-status-confirmation/version-conflict.png)
- [手机错误态 390px](screenshots/product-status-confirmation/production-error-390.png) / [320px](screenshots/product-status-confirmation/production-error-320.png)
- [商品删除确认框](screenshots/product-status-confirmation/messagebox-delete.png)
