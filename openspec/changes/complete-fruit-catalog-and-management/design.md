# Design

## Context

动机参见 `proposal.md`，外部行为以四份 `specs/*/spec.md` 为准。本次覆盖前后端、数据库和文件存储，包含事务、授权及增量迁移，因此需要技术设计。

实际检查结果：

- `front-end/src/router.ts` 已有公开首页、买家个人中心子路由和单页管理员工作台；`HomeView.vue` 与 `AdminView.vue` 仍为占位内容。
- 前端已经安装 Element Plus，但 `main.ts` 未全局注册；现有 Axios 客户端会自动附带 JWT，401 时清除会话。沿用 Pinia 认证和现有绿色视觉规范。
- 后端已采用 controller/service/mapper、`ApiResponse`、JWT BUYER/ADMIN 角色；`SecurityConfig` 尚未放行商品接口，未配置分页插件。
- Flyway 当前有 V1 账户、V2 地址，测试使用另行维护的 `test-schema.sql` 和 H2，关闭 Flyway；因此 H2 测试不能代替实际 MySQL 迁移验证。
- `AvatarStorageService` 已有本地文件名、类型/签名/大小校验及文件读取机制，multipart 单文件为 5 MiB；全局超限错误文案目前专指头像。
- 没有商品、库存、收藏、评价或订单表。主规范清单为空，但原平台变更已规划相同能力名称、多 SKU、库存预留、本地图片和订单完成后评价。

用户已确认：人工推荐加最新商品补充、评价只读、商品仅归属二级分类、商品编辑可调整库存、商品逻辑删除、关联分类禁止删除、售罄仍展示、失效收藏保留提示。本文给出可审核的具体默认值，不引入额外业务模块。

## Goals / Non-Goals

**Goals:**

- 让所有公开商品查询共享同一可见性和库存口径，避免推荐、详情与收藏彼此矛盾。
- 商品、SKU、库存流水和成功操作记录在数据库事务中一致提交，保留稳定 SKU 标识供未来交易引用。
- 基于现有认证与文件基础建设完整页面和真实 API，使用增量迁移保护现有账户及地址数据。
- 提供可复现的规格筛选、角色隔离、并发保存和文件验证验收。

**Non-Goals:**

- 本阶段不创建订单/购物车表，不增加购买按钮或模拟无效交易；评价没有写操作。
- 不实现采购、出入库、盘点或审计查询工作台；本阶段仅有商品编辑库存调整及持久记录。
- 不建设轮播图、公告、销量/个性化推荐、批次库存、云存储或新的认证体系。
- 不在本轮规划期间编辑项目代码、执行应用迁移、提交或推送实现。

## Decisions

### 1. Module structure and API roles

后端新增 `catalog`、`inventory`、`review` 及最小 `audit` 模块，每个模块沿用 DTO/entity/mapper/service/controller 职责。商品管理 service 调用库存和审计 service 并复用外层事务；不使用跨服务网络调用或异步库存同步。

接口均沿用 `/api/v1` 和 `{code, message, data}`。只读 DTO 单独定义，避免直接序列化数据库实体泄露内部字段。

| 角色 | 方法与地址 | 行为 |
| --- | --- | --- |
| 匿名 | `GET /api/v1/public/categories` | 有效两级分类树 |
| 匿名 | `GET /api/v1/public/products` | 分页组合搜索 |
| 匿名 | `GET /api/v1/public/products/recommendations` | 最多 8 件推荐及补充商品 |
| 匿名 | `GET /api/v1/public/products/{productId}` | 详情及评价摘要 |
| 匿名 | `GET /api/v1/public/products/{productId}/reviews` | 已发布评价分页 |
| 匿名 | `GET /api/v1/public/product-images/{filename}` | 受控商品图片读取 |
| BUYER | `GET /api/v1/buyer/favorites` | 本人收藏分页 |
| BUYER | `GET /api/v1/buyer/favorites/{productId}` | `{favorited: boolean}`，无需商品仍可见 |
| BUYER | `PUT /api/v1/buyer/favorites/{productId}` | 收藏可见商品，幂等 |
| BUYER | `DELETE /api/v1/buyer/favorites/{productId}` | 取消本人收藏，幂等 |
| ADMIN | `GET/POST /api/v1/admin/products` | 商品分页查询/新增下架商品 |
| ADMIN | `GET/PUT /api/v1/admin/products/{productId}` | 查询/完整编辑商品及 SKU |
| ADMIN | `PATCH /api/v1/admin/products/{productId}/status` | 目标上架状态及期望版本 |
| ADMIN | `DELETE /api/v1/admin/products/{productId}?version=...` | 按期望版本逻辑删除 |
| ADMIN | `POST /api/v1/admin/product-images` | 一次上传一张图片并返回受控引用 |
| ADMIN | `GET/POST /api/v1/admin/categories` | 完整分类树/新增分类 |
| ADMIN | `GET/PUT/DELETE /api/v1/admin/categories/{categoryId}` | 分类单项查询/编辑/删除 |

分类编辑请求携带名称、父分类、排序和启用状态。商品 PUT 不接受买家身份或库存 `reserved` 字段，状态仅经 status 接口变更；新增先保存为下架，再调用上架操作。`recommendations` 为固定集合路由，避免与数字商品标识路由混淆。

只放行明确的商品 GET 路径及图片 GET 路径，保持 ADMIN/BUYER 规则原样。公共目录请求使用不附带 JWT 的 Axios 实例或显式匿名请求选项，复用 baseURL/timeout；收藏继续走现有认证客户端。这样过期本地 token 不会阻止无需登录的浏览，也无需在公开商品 DTO 嵌入登录状态。

列表 DTO 为 `{items, total, page, size}`，默认 `page=1,size=12`，size 最大 100；收藏/评价沿用相同分页外壳。参数错误 400、未登录 401、错误角色 403、商品不可用 404、版本/关联/库存业务冲突 409、文件超限 413，统一通过当前异常机制返回。增加查询绑定错误映射，避免参数错误落入 500。

备选：把登录态嵌入公开商品响应会增加缓存及过期 token 的耦合；通用实体 CRUD 会暴露预留库存和内部记录，因此采用分角色 DTO 和专用 service。

### 2. Persistence and stable identifiers

新增 V3 起的增量迁移，不改写 V1/V2。以下字段为本次数据契约，时间使用现有数据库时间策略，库存整数、价格 `DECIMAL(10,2)` / Java `BigDecimal`。金额 JSON 用十进制字符串，前端将表单值转换为受控十进制字符串提交，不以浮点加减作为价格来源。

| 表 | 核心字段及约束 |
| --- | --- |
| `product_category` | `id,parent_id,name,sort_order,status,created_at,updated_at`；自关联 FK；父为空表示一级；根分类名称也须唯一，使用 `COALESCE(parent_id,0)` 的生成作用域列和 `(parent_scope,name)` 唯一索引，避免 NULL 父项绕过唯一性 |
| `fruit_product` | `id,category_id,name,description,origin,taste,storage_method,status,is_recommended,recommend_sort,published_at,deleted,version,created_at,updated_at`；分类 FK；上架 1/下架 0；删除独立标记 |
| `product_sku` | `id,product_id,specification,price,status,sort_order,created_at,updated_at`；稳定 ID；商品内规格唯一，包括停用规格，重复名称应恢复原 SKU 而非创建替代 ID |
| `product_image` | `id,storage_key,uploaded_by,product_id,active,sort_order,created_at`；上传后 `product_id` 可空，保存商品后绑定；`storage_key` 唯一；关联商品不可共享同一图片记录；移出当前图库后 `active=0` 并保留引用 |
| `sku_inventory` | `sku_id,on_hand,reserved,low_stock_threshold,version,updated_at`；SKU 一对一 FK；数量非负且 `on_hand>=reserved` |
| `user_favorite` | `id,buyer_id,product_id,product_name_snapshot,main_image_snapshot,created_at`；买家/商品 FK；`(buyer_id,product_id)` 唯一 |
| `inventory_movement` | `id,sku_id,operation_log_id,type,before_on_hand,after_on_hand,quantity_delta,reason,admin_id,created_at`；类型 `INITIAL` 或 `PRODUCT_EDIT_ADJUSTMENT`；记录实际差额 |
| `product_review` | `id,product_id,sku_id,buyer_id,order_item_id,rating,content,specification_snapshot,status,created_at`；商品/SKU/买家 FK；评分 1..5；`order_item_id` 非空且唯一，本阶段暂不建立指向不存在订单表的 FK |
| `admin_operation_log` | `id,admin_id,operation_type,target_type,target_id,result,created_at`；管理员 FK；成功结果及白名单字段 |

字段默认限制：商品名称 1..100 字符，分类名称 1..50，规格 1..100，描述最多 5000，产地/口感/储存方法各最多 500，SKU 最多 20 个，推荐/显示排序为非负整数，库存 0..2147483647，阈值默认 0；名称首尾去空白。价格范围为 0.01..99999999.99。描述及评价按普通文本保存和显示。

查询索引覆盖商品分类/状态/删除/发布时间、SKU 商品/启用/价格、收藏买家/时间、评价商品/发布/时间和库存流水 SKU/时间；外键删除用 RESTRICT，业务删除不级联丢弃库存及评价。

分类树最大两层由 service 校验并锁定关联分类后复核；数据库 FK 和唯一键兜底。迁移同步维护 H2 schema 的等价约束，MySQL 生成列等方言差异单独验证。

备选：商品直接包含单一价格库存无法承接规格；重新插入所有 SKU 会破坏历史引用；立即创建订单表只为评价 FK 会扩大本次范围，因此选择稳定 SKU 和订单关联列后续补 FK。

### 3. Product save, publication and category consistency

商品编辑表单发送商品 `version`，已有 SKU 携带 `id` 和库存 `inventoryVersion`，新 SKU 不带 ID。事务先锁定商品记录并检查版本，再按 SKU ID 升序锁定库存记录并检查版本与预留量；所有已有 SKU 必须属于当前商品。

保存遵循“更新已有 SKU、新建新增 SKU、停用移除 SKU”；不删除旧库存、流水或评价，停用 SKU 如仍有预留量则拒绝保存。停用规格在后台编辑中可再次启用，保留原 ID。同一请求中的 SKU 重命名先检查最终名称唯一性，并以避免临时唯一键冲突的更新策略保存；不因调整顺序误判重复。

保存商品图片关联、SKU、库存及成功审计后增加商品版本；实物库存或阈值变化时增加库存版本，仅实物数量变化时写库存流水。版本冲突返回 409 `VERSION_CONFLICT`，前端保留草稿并提示重新读取；不自动覆盖服务器。上架、下架和删除也携带商品版本，重复相同目标状态不写重复记录。

已上架商品编辑时重新验证图片和有效启用 SKU 等上架条件，不允许移除全部图片或全部 SKU 后继续保持上架。分类启停单独改变有效可见性，不批量改写商品状态。商品上架前锁定二级分类及一级祖先并复核启用状态；商品创建、分类迁移及分类结构更新使用一致的分类 ID 锁顺序并在锁定后复核树形关系。创建商品或子分类与删除分类必须串行复核引用，避免并发产生孤儿或第三级。FK/唯一约束冲突转换为业务 409。

分类修改禁止将含子分类的根节点下移，也禁止将有关联商品的二级分类提升为根节点；可将二级分类迁移到另一一级分类。删除分类时检查所有关联商品，包括逻辑删除商品；曾使用的分类仍可通过停用退出商城。分类未被使用时可物理删除。

商品删除设置 `deleted=1,status=0`，保留 SKU、库存、图片及收藏；仍有任一 SKU 预留时返回冲突。删除后的管理详情返回不存在，不提供回收站或恢复操作。下架不释放库存预留，未来订单履约根据订单快照与稳定 ID 继续处理。

`published_at` 在下架转上架时记录本次上架时间，已上架商品的普通编辑不会改变它；再次上架视为最新上架。推荐设置保存于商品，不因分类停用而被清空。

备选：只使用行锁会串行执行但仍允许旧表单覆盖新数值；采用版本加行锁同时防止丢失更新和破坏预留约束。级联删除与物理 SKU 删除不满足历史保留要求。

### 4. Inventory adjustments and audit boundaries

新 SKU 建立库存行，`reserved=0`、库存版本初始为 0；正初始库存写 `INITIAL` 流水，初始 0 不写无意义流水。已有库存以服务端锁定的前值计算差额，不信任客户端差额；实物量未变则只更新必要阈值，不写库存流水。

预留字段仅供未来订单 service 改动，本次 DTO 对未知预留字段明确拒绝，不更改全局 JSON 兼容设置。未来所有预留、发货或库存写路径必须使用一致的库存锁及版本增量，避免本次表单绕过更新检测。

商品编辑成功创建一笔 `PRODUCT_UPDATE` 管理记录；每个实际调整的 SKU 创建关联的 `SKU_STOCK_ADJUSTMENT` 记录及对应流水。成功审计加入外层事务，任何一步失败全回滚。分类和商品其他变更也记录白名单元数据。拒绝/失败在 service 边界以结构化日志记录操作类型、已知管理员/目标和错误码，不输出完整请求体或 token；本阶段不建立失败日志查询接口。

后台 SKU 行显示实物/预留/可售及阈值，`available<threshold` 标记低库存。商城只返回可售数量，不返回管理字段、阈值或库存流水。

备选：异步记录审计或库存流水可能遗漏失败边界，直接覆盖库存且不留流水无法追溯，因此本阶段采用同库事务及最小记录。

### 5. Public queries, recommendations and price semantics

公开可见谓词集中复用：商品未删除且上架、二级分类及一级祖先启用、至少一个启用 SKU。零库存不改变可见性。公开详情及评价找不到可见商品统一 404；收藏失效只暴露已保存的收藏摘要。

`GET products` 参数：`keyword,categoryId,minPrice,maxPrice,sort,page,size`；`sort` 为 `NEWEST`（默认）、`PRICE_ASC`、`PRICE_DESC`。关键词最大 100 字符，去首尾空白，空值视为无条件；名称或描述使用参数绑定的 LIKE，并将 `%`、`_` 等作为文本转义。价格上下限为闭区间，同一个启用 SKU 必须同时满足上下限，采用 EXISTS 避免跨 SKU 拼接命中或重复商品。

先对商品维度查询总数及分页 ID，再批量读取主图和 SKU 聚合，避免对一对多 JOIN 直接分页及 N+1。DTO 给出 `minPrice,maxPrice,availableStock,soldOut`；最低价聚合包含全部启用 SKU（包括零库存），因此筛选 30..50 仍可能看到“19.90 元起”，详情可见命中规格 39.90，此行为在 spec 中明确。

最新排序使用 `published_at DESC,id DESC`；价格排序使用最低价及 `id DESC`。管理员列表另外支持 `status,recommended`，按 `updated_at DESC,id DESC`，不复用商城强制上架条件。不存在或不可用的公开分类返回空页，不自动扩大为全部商品。

推荐查询先取 `is_recommended=1` 的可见商品，按 `recommend_sort ASC,id DESC` 最多 8 条，再按最新上架补足尚未选中商品；人工数量已足够时不补充，数据不足时按实际返回，无需推荐服务或销量数据。

分页使用 MyBatis-Plus 分页支持或明确 LIMIT/count mapper；当前无分页插件，如采用插件需补相应拦截器及匹配版本的 JSQLParser 分页依赖。参数经白名单映射，绝不将客户端 sort 拼接进 SQL。

备选：按商品最低价过滤会遗漏高价规格，按库存大于 0 过滤会隐藏已确认应展示的售罄商品；全文搜索和销量推荐在本阶段没有必要数据基础。

### 6. Image upload and binding lifecycle

商品上传使用独立 `PRODUCT_IMAGE_STORAGE_DIR`（默认 `./uploads/products`），沿用头像的 JPEG/PNG/WebP、5 MiB、随机文件名和受控读取原则，增加实际图片解码验证，拒绝仅有文件签名但损坏的内容。JPEG/PNG 使用 Java ImageIO，WebP 增加 TwelveMonkeys `imageio-webp` 的 Java 17 兼容版本并固定 Maven 依赖；用真实三类图片验证解码支持，解码失败则拒绝上传。不复用头像绑定买家和替换旧头像的业务 service。

每次 POST 只接受一个 `file`，返回 `{imageId,url}`；上传阶段将文件写入随机受控路径并建立未绑定的 `product_image` 元数据，数据库失败则清理新文件。商品请求携带有序 `imageIds`，只能使用当前管理员上传的未绑定图片或本商品已有图片，禁止绑定其他商品图片或任意外部 URL。主图为顺序 0，不另存一个可能失同步的主图字段。

取消编辑留下未绑定图片，可由本地显式清理工具删除超过 24 小时的未绑定记录/文件；本次不增加自动定时清理服务。商品编辑移除的已绑定图片设置 `active=0`，保留文件和元数据，避免收藏主图摘要失效；图库及主图只查询 active 图片，后台可以重新选用本商品已保留的图片。图片文件路径永不直接进入 API，读取仅接受受控文件名、不支持路径穿越或目录列举，并设置正确 MIME 和 `nosniff`。

multipart 请求仍单文件上传，不为 10 张图片增加单次请求上限；全局超限错误改为通用图片上传文案，保持原头像限制和测试行为。前端逐张上传，全部完成后才允许保存商品，使用排序和移除操作，无需裁剪或第三方存储。

备选：整件商品携带多张 multipart 图片使重试和事务边界复杂；无元数据的任意 URL 会失去受控引用校验；即时删除旧文件会破坏收藏快照，因此选择上传后绑定和保留旧引用。

### 7. Favorites and read-only reviews

收藏写入从 JWT subject 获取买家并复核账户有效性。PUT 先锁定商品并检查可见性，使用数据库 `(buyer_id,product_id)` 唯一键及幂等写入去重；重复收藏不刷新创建时间或快照。取消使用本人及商品组合删除，不检查公开商品状态。收藏分页先查本人行，再批量查询可见商品，失效行返回 `{productId,nameSnapshot,mainImageSnapshot,available:false,unavailableReason}`，不附加当前价格、SKU 或描述；可见行返回实时商品卡片 DTO。

失效原因采用 `DELETED`、`UNLISTED`、`CATEGORY_DISABLED`，存在多个原因时按此优先级；售罄仍 `available=true,soldOut=true`。取消与收藏只修改当前买家行，客户端不能指定 owner。

评价仅查 `product_id` 及发布状态，分页排序 `created_at DESC,id DESC`，平均值独立聚合全部发布记录，空值保持 null。返回评价标识、星级、文字、规格快照、时间及昵称；昵称缺失用“买家”，不退回可能敏感的用户名或手机号。

新评价表没有公开写入接口、管理员代写或应用启动演示评价。测试在隔离 schema 中插入固定评价记录验证读取；正常新库自然显示“暂无评价”。`order_item_id` 暂作非空唯一关联标识，订单模块实现时再创建订单 FK、订单归属和完成校验，以及同一明细一次评价的写流程。这是已有整体评价方案的分阶段实现，未把未购买评价改为允许行为。

备选：收藏级联删除会丢失失效状态；直接透出下架商品违背公开可见性；提前开放通用评价 POST 会绕过已购约束。

### 8. Frontend routes and interaction

| 路由 | 页面与交互 |
| --- | --- |
| `/` | 更新首页：商城顶栏、搜索入口、分类导航、推荐商品卡片、查看全部商品 |
| `/products` | 分类/关键词/价格筛选、排序和分页，以 URL query 为唯一已提交条件来源 |
| `/products/:productId` | 图库、分类路径、商品信息、SKU 切换、可售数量、评价摘要/分页、收藏 |
| `/account/favorites` | 复用买家布局，分页卡片、失效提示及取消收藏 |
| `/admin` | 管理员布局，默认重定向商品列表，保留角色守卫 |
| `/admin/products` | 后台表格、组合筛选、状态/推荐信息、上架下架和删除确认 |
| `/admin/products/new`、`/admin/products/:productId/edit` | 独立编辑页，商品信息、图片、SKU 表格、推荐标记及顺序 |
| `/admin/categories` | 两级树、新增/编辑面板、启停排序和删除确认 |

公共列表/详情复用商城头部及 `ProductCard`；API 类型放入 catalog、admin catalog、favorites 对应文件，避免把所有业务塞入当前 buyer API。Element Plus 在新页面按组件显式导入及样式引入，保持现有账户页面行为。

搜索表单区分未提交输入与 URL 已提交条件；点击搜索、分类或排序通过路由更新重置 page=1，刷新/后退复原 query，处理快速输入/切页时旧请求迟到覆盖新结果的问题。详情 SKU 默认按 `sort_order,id` 稳定顺序选首个有库存项。收藏状态仅在有效 BUYER 会话下单独读取，游客点击跳转 `/login?redirect=...`，登录后只返回页面不自动重放收藏；管理员浏览商城无买家收藏入口。

库存字段区分可编辑实物量与只读预留/可售量，版本冲突时提示刷新且保留草稿；不展示后端类名、版本号或流水标识等内部细节。新增/编辑成功后回商品列表，删除和下架有确认反馈，删除最后一条时处理页码回退。

延续绿色、浅背景和简洁表单，补齐卡片/图库/后台表格视觉。桌面使用分类与列表、详情图文分栏；手机改为单栏、横向分类与可滚动 SKU 表格。所有新页具备加载、空态、错误及重试，表单字段有标签，按钮在提交时避免重复提交。

备选：将复杂 SKU 和多图编辑塞入小对话框会限制移动端及版本冲突处理，独立页面更适合本次字段规模。

### 9. Verification and delivery

- 后端 MockMvc/H2 覆盖分类树/层级/重名/删除限制，商品与 SKU CRUD/事务失败/发布条件/稳定 ID，公开可见性及匿名读，价格闭区间/多规格/重复消除/排序分页，推荐排序补足，售罄，收藏归属/幂等/失效恢复，图片限制/引用/路径，评价统计/隐私/只读及审计记录。
- 对并发同版本商品保存、库存低于预留、并发重复收藏、分类删除与商品归属变更增加实际事务集成验证；测试可直接设置 reserved，以模拟未来订单状态，不创建假的订单 API。
- 在隔离 MySQL 8 验证全新迁移及已有 V1/V2 数据库升级，再次启动无重复迁移，验证生成列、唯一/FK/CHECK 约束及真实锁行为。不得对用户日常数据库执行破坏性验证。
- 前端执行 `npm run typecheck` 和 `npm run build`；联调覆盖管理员新建分类→新增多图多 SKU 商品→推荐上架→游客搜索/详情→买家收藏→后台改价/库存→下架/删除后失效收藏→取消收藏；同时检查桌面和手机布局。
- 测试评价格式用隔离夹具；演示商品通过受保护管理界面建立或显式本地演示数据工具准备，不在生产 Flyway 中自动插入虚假评价或占位图片链接。

## Risks / Trade-offs

- 多规格查询直接 JOIN 分页会重复记录并错算总数 → 按商品维度 EXISTS/count/分页，再批量组装展示数据。
- 旧表单覆盖最新库存或未来预留 → 商品和库存版本、行锁及前值复核，冲突明确返回 409。
- 分类变更与商品关联并发可能破坏两层约束 → 关联分类锁后复核、统一锁顺序、FK 与唯一键兜底，并在 MySQL 上验证。
- 文件系统不随数据库事务提交，取消编辑或进程中断留下孤立文件 → 随机不可变文件、上传元数据、失败清理及显式未绑定清理；保留被收藏引用的旧主图，接受单机少量存储增长。
- H2 通过不能证明 MySQL 生成列、迁移或行锁正确 → 增加独立 MySQL 升级及并发验证，不依赖纯 H2 结论。
- 正常新库暂时无真实评价 → 真实展示暂无评价，读取由隔离测试验证，待订单完成后开放写入；不伪造已购评价。
- 逻辑删除商品仍引用分类，使该分类不能删除 → 提供停用分类退出商城，界面明确关联限制，不自动删历史引用。

## Migration Plan

1. 用户审核完整规划并明确要求应用后，按任务实现；只新增 V3 起迁移并同步测试 schema，不修改已应用 V1/V2。
2. 先部署数据表与商品图片目录配置，再部署后端 API 和依赖这些 API 的前端页面；更新 `.env.example` 和 README 时保留已有用户编辑并只追加必要说明。
3. 在隔离环境验证已有买家/地址保留、迁移幂等、图片读取、权限和商品闭环。演示商品显式准备，不将上传文件、本地配置或凭据提交版本库。
4. 如回滚，回退应用版本并保留新增表、库存流水、操作记录及文件；数据库采用后续修复迁移，不自动 drop 表或清理历史收藏图片。
5. 后续订单模块增加订单明细和评价 FK、完成校验及评价写入；库存模块沿用本次锁、版本及流水协议扩展完整库存作业，不另建冲突库存字段。
