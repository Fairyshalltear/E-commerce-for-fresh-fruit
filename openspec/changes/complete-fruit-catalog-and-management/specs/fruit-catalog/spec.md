# Spec Delta

## Purpose

为访客和买家提供可按分类、关键词和价格浏览的水果商品目录，并让管理员维护商品、规格、图片和两级分类。通过统一的可见性、规格价格及收藏失效规则，使商城展示与后台管理保持一致，为后续交易能力提供商品基础。

## ADDED Requirements

### Requirement: Administrator manages a two-level category tree
系统 SHALL 允许管理员查询、新增、编辑、排序、启停及删除最多两级的分类。分类名称去除首尾空白后不得为空，同一父分类下不得重名；一级分类无父分类，二级分类只能归属一级分类，分类不得指向自身或形成循环。商品 SHALL 仅归属二级分类。

#### Scenario: Administrator creates and edits categories
- **WHEN** 管理员建立一级分类及其二级分类，并修改名称或排序
- **THEN** 系统保存这些信息，分类树按排序值升序及分类标识升序返回

#### Scenario: Invalid hierarchy is rejected
- **WHEN** 管理员尝试创建第三级分类、设置自身为父分类或将含子分类的一级分类改为二级分类
- **THEN** 系统拒绝变更，保留原分类树

#### Scenario: Product is assigned to a first-level category
- **WHEN** 管理员将商品保存到一级分类或不存在的分类
- **THEN** 系统拒绝保存并提示必须选择有效二级分类

### Requirement: Category availability and deletion preserve product relationships
系统 SHALL 只向商城提供自身及祖先均启用的分类和商品。停用一级分类 SHALL 使其二级分类及商品暂时不可在商城浏览，但不改写商品原上架状态；重新启用后按各自状态恢复可见性。存在子分类或任何关联商品（包括逻辑删除商品）的分类 SHALL 禁止删除；有关联商品的二级分类 SHALL 禁止提升为一级分类。

#### Scenario: Parent category is disabled and enabled again
- **WHEN** 管理员停用后重新启用一级分类
- **THEN** 停用期间其下商品不出现在推荐、搜索或公开详情中；重新启用后，仅原已上架且二级分类启用的未删除商品恢复展示

#### Scenario: Referenced category cannot be deleted
- **WHEN** 管理员删除含子分类或关联商品的分类
- **THEN** 系统拒绝删除，提示关联限制且保留分类及其关联数据

#### Scenario: Referenced second-level category cannot become a root
- **WHEN** 管理员将有关联商品的二级分类改为一级分类
- **THEN** 系统拒绝修改，保留商品仅归属二级分类的约束

### Requirement: Administrator lists and filters products
系统 SHALL 提供管理员商品分页列表，支持商品名称关键词、一级或二级分类、上架状态和推荐标记的组合筛选。默认列表 SHALL 排除逻辑删除商品，显示主图、名称、分类、启用 SKU 价格范围、可售库存合计、上架状态、推荐标记及更新时间，并按更新时间倒序、商品标识倒序排列。

#### Scenario: Administrator combines filters and changes pages
- **WHEN** 管理员按分类、关键词和下架状态查询第 2 页
- **THEN** 系统只返回同时符合条件的商品，包含正确的记录总数与分页信息，且同一商品不因多个 SKU 重复出现

### Requirement: Administrator creates and edits products with stable SKUs
系统 SHALL 允许管理员新增及编辑商品名称、二级分类、图片、描述、产地、口感、储存方法、推荐设置及一个或多个 SKU。每个 SKU SHALL 有稳定标识、非空规格、独立正价格、非负整数库存和低库存阈值；价格最多两位小数，同一商品的规格名称不得重复。新商品 SHALL 默认为下架，可只含一个默认规格。商品及 SKU 保存 SHALL 原子成功或全部失败。

#### Scenario: Administrator saves a product with multiple specifications
- **WHEN** 管理员为苹果保存 500g 和 1kg 两个不同价格、库存的 SKU
- **THEN** 系统保存一件商品及两个独立规格，后续编辑保持已有 SKU 标识不变

#### Scenario: Invalid specification rolls back the entire edit
- **WHEN** 保存请求包含重复规格、零或负价格、超过两位小数的价格、负库存或其他商品的 SKU 标识
- **THEN** 系统拒绝请求，商品信息及所有 SKU 不发生部分修改

#### Scenario: Administrator retires a specification
- **WHEN** 管理员移除没有预留库存的已有 SKU
- **THEN** 系统将该 SKU 停用并保留标识和历史库存，不再向商城展示该规格

#### Scenario: Reserved specification cannot be retired
- **WHEN** 管理员移除仍有预留库存的 SKU
- **THEN** 系统拒绝保存，保留原 SKU 和预留库存

### Requirement: Product images support controlled upload and ordering
系统 SHALL 允许管理员上传 JPEG、PNG 或 WebP 商品图片，单张不超过 5 MiB，每个商品最多关联 10 张图片，并支持移除和排序，以第一张作为主图。服务端 SHALL 校验文件类型、内容和受控图片引用；公开读取 SHALL 不暴露服务器文件路径或目录列表。

#### Scenario: Administrator uploads and orders images
- **WHEN** 管理员上传有效图片并将其调整为商品第一张图片后保存
- **THEN** 后台、商品卡片和商品详情使用相同主图，详情图库按保存的顺序展示

#### Scenario: Invalid image upload or reference is rejected
- **WHEN** 请求包含伪造图片内容、超限文件、超过图片数量限制或非受控图片引用
- **THEN** 系统拒绝上传或保存并给出可识别错误，不产生可用的非法图片引用

### Requirement: Product publication and deletion govern public visibility
系统 SHALL 允许管理员上架、下架和逻辑删除商品。上架 SHALL 要求名称、有效且启用的二级分类及祖先、至少一张图片和至少一个有效启用 SKU；零可售库存 SHALL 不阻止上架。下架或删除后商品 SHALL 从公开列表、推荐和详情中不可见；删除 SHALL 保留商品、SKU、库存和收藏关联，且有预留库存时禁止删除。本次 SHALL 不提供恢复删除商品的入口。

#### Scenario: Incomplete product cannot be published
- **WHEN** 管理员上架缺少图片、没有启用 SKU 或分类停用的商品
- **THEN** 系统拒绝上架并指出需补齐的内容

#### Scenario: Editing a published product cannot remove its publication requirements
- **WHEN** 管理员编辑已上架商品并移除全部图片或全部启用 SKU
- **THEN** 系统拒绝保存并保留原商品，提示先下架或保留有效商品信息

#### Scenario: Unlisted or deleted product is opened directly
- **WHEN** 访客通过已有链接请求下架或逻辑删除商品的详情或评价
- **THEN** 系统返回商品不可用的统一不存在响应，不泄露后台商品内容

#### Scenario: Product with outstanding reservations cannot be deleted
- **WHEN** 管理员删除含预留库存的商品
- **THEN** 系统拒绝删除，保留商品及全部关联数据

### Requirement: Homepage recommendations use manual selection only
系统 SHALL 允许管理员设置商品推荐标记和排序值。首页推荐区 SHALL 仅展示最多 8 件同时满足公开可见性规则且已明确标记为推荐的商品，并按推荐排序升序、商品标识倒序排列。未标记为推荐的商品 SHALL NOT 出现在首页推荐区；推荐数量不足时返回实际数量，不以其他商品补足。推荐商品允许售罄并标记售罄。

#### Scenario: Homepage shows only manually selected recommendations
- **WHEN** 当前有 3 件可见推荐商品及至少 5 件其他可见商品
- **THEN** 首页只展示按设置顺序排列的 3 件推荐商品

#### Scenario: No products are manually recommended
- **WHEN** 当前没有可见推荐商品
- **THEN** 首页推荐接口返回空列表，不展示未推荐商品

#### Scenario: Recommended product becomes unavailable
- **WHEN** 推荐商品下架、删除或所属分类不可用
- **THEN** 它不再出现在推荐结果中，接口返回其余可见推荐商品，数据不足时返回实际数量

### Requirement: Visitors browse and search with combined filters
系统 SHALL 无需登录即可提供公开分类树和商品分页搜索，支持商品名称或描述的关键词、分类、最低价、最高价组合筛选，以及最新上架、价格升序和降序排序。关键词 SHALL 按文本匹配而非查询语法解释；一级分类筛选包含其有效二级分类。默认 SHALL 为最新上架排序，所有排序 SHALL 以商品标识补充稳定顺序。

#### Scenario: Visitor searches across descendant categories
- **WHEN** 访客同时提交关键词和一级分类
- **THEN** 系统只返回该一级分类的有效二级分类中同时命中名称或描述关键词的公开商品

#### Scenario: Empty or unavailable category is searched
- **WHEN** 访客选择不存在、停用或因祖先停用而不可用的分类，或关键词没有命中
- **THEN** 系统返回记录总数为 0 的有效空页，前端展示无匹配商品状态

### Requirement: Price filtering uses SKU prices with inclusive boundaries
价格筛选 SHALL 以至少一个启用 SKU 的价格同时落在给定闭区间为命中条件，而不是商品最低价落入区间；缺失最低价或最高价表示该侧不限。商品卡片和价格排序 SHALL 使用全部启用 SKU 的最低价，并在不同规格价格不同时显示起售价。下架 SKU SHALL 不参与筛选或起售价计算，零库存启用 SKU SHALL 仍参与。

#### Scenario: Higher-priced SKU matches while starting price is lower
- **WHEN** 某商品启用 SKU 价格为 19.90 和 39.90，访客筛选 30.00 至 50.00
- **THEN** 商品命中一次，卡片显示 19.90 元起，详情显示两种规格价格

#### Scenario: Price boundaries match inclusively
- **WHEN** SKU 价格恰好等于提交的最低价或最高价
- **THEN** 该 SKU 满足对应边界条件

### Requirement: Query parameters and pagination are validated
所有商品、收藏和评价分页 SHALL 使用从 1 开始的页码，默认每页 12 条，允许每页 1 至 100 条，并返回页码、页大小、总数和记录。系统 SHALL 拒绝非法页码、页大小、排序值、负价格、超过两位小数的价格或最低价大于最高价；超出最后一页 SHALL 返回空记录并保留实际总数。

#### Scenario: Invalid query is rejected
- **WHEN** 请求包含 page=0、size=101、非法排序值或倒置的价格区间
- **THEN** 系统返回参数校验错误，前端保留已输入筛选条件并展示错误

### Requirement: Public product details reflect selected specifications and availability
系统 SHALL 为公开可见商品展示有序图片、商品信息、有效分类路径、全部启用 SKU 的规格、价格和可售库存及评价摘要。前端 SHALL 默认选择第一个有可售库存的 SKU，无可售 SKU 时选择第一个启用 SKU；切换规格 SHALL 同步更新价格和库存。零库存商品 SHALL 仍可浏览并明确显示售罄，不显示为有货。

#### Scenario: Buyer changes specification
- **WHEN** 买家从 500g 切换到 1kg 规格
- **THEN** 页面显示 1kg 的独立价格及可售库存，不使用其他规格的库存

#### Scenario: All specifications are sold out
- **WHEN** 商品全部启用 SKU 的可售库存为 0
- **THEN** 商品卡片及详情显示售罄，商品仍可浏览及收藏

### Requirement: Buyers manage isolated and idempotent favorites
系统 SHALL 允许已登录且有效的买家收藏公开可见商品、取消收藏和分页读取自己的收藏，并提供给定商品的收藏状态。收藏 SHALL 以买家和商品去重，重复收藏可见商品及重复取消 SHALL 成功而不增加重复记录；取消 SHALL 不要求商品仍可见。买家身份 SHALL 来自认证信息，不接受客户端指定其他买家身份。

#### Scenario: Repeated favorite requests do not duplicate records
- **WHEN** 同一买家重复或并发收藏同一可见商品
- **THEN** 仅保留一条该买家的商品收藏，不影响其他买家的记录

#### Scenario: Buyer cancels an unavailable favorite
- **WHEN** 买家取消已经下架或删除的收藏商品
- **THEN** 系统成功移除该买家收藏，重复取消仍成功

#### Scenario: Unauthorized favorite access is rejected
- **WHEN** 访客、管理员或试图指定其他买家身份的请求操作买家收藏
- **THEN** 系统拒绝未授权访问，不返回或修改其他买家的收藏

### Requirement: Favorite lists retain unavailable products safely
收藏列表 SHALL 按收藏时间倒序和收藏标识倒序分页。失效收藏 SHALL 保留收藏时的名称和主图摘要、失效原因及取消入口，不返回当前下架商品详情、价格或 SKU；前端 SHALL 禁止进入失效商品详情。商品仅售罄 SHALL 不视为收藏失效，恢复可见的商品 SHALL 自动恢复正常收藏展示。

#### Scenario: Favorite becomes unavailable and later returns
- **WHEN** 已收藏商品被下架并随后重新上架到有效分类
- **THEN** 下架期间收藏显示商品不可用及取消入口，重新上架后同一收藏恢复实时商品展示

### Requirement: Catalog pages preserve navigation and user feedback
商城 SHALL 提供首页、商品列表、详情及买家个人中心收藏入口；管理端 SHALL 提供商品列表、独立新增/编辑页和分类树入口。搜索条件、排序及页码 SHALL 保存在地址参数中，切换筛选重置到第一页。页面 SHALL 提供加载、空数据、请求错误及重试状态，支持手机和桌面浏览；未登录点击收藏 SHALL 引导买家登录并在成功后返回原页面，不自动提交收藏。

#### Scenario: Search URL is revisited
- **WHEN** 用户刷新或返回含筛选及页码的商品列表地址
- **THEN** 页面恢复相同条件并请求对应数据

#### Scenario: Visitor signs in from a product detail
- **WHEN** 未登录访客在商品详情点击收藏并完成买家登录
- **THEN** 页面返回该商品详情，用户可以重新点击收藏完成操作
