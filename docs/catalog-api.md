# 商品模块 API 与验收

响应沿用 `{code,message,data}`。公共 GET 不需要 JWT；收藏使用 BUYER token，管理操作使用 ADMIN token。分页 `page` 从 1 开始，默认 `size=12`，允许 1..100，返回 `{items,total,page,size}`。金额为十进制字符串，平均评分为数字或 null。

## 分类

- `GET /api/v1/public/categories`：自身及祖先均启用的两级分类树。
- `GET /api/v1/admin/categories`：完整分类树；`GET /api/v1/admin/categories/{id}`：单项。
- `POST /api/v1/admin/categories`、`PUT /api/v1/admin/categories/{id}`：保存名称、父级、排序和启用状态。
- `DELETE /api/v1/admin/categories/{id}`：无子分类且无任何关联商品时可删除，包括逻辑删除商品的关联；存在关联返回 409，可改为停用。

请求示例：

```json
{"name":"苹果","parentId":1,"sortOrder":0,"status":1}
```

parentId=null 为一级，非空必须指向一级。同父级名称唯一，根分类也唯一；最多两级，不允许自环、带子分类的一级下移或有关联商品的二级提升。status=1 启用、0 停用。分类停用不改写商品原上架状态，祖先恢复后按原状态恢复可见性。

## 商品管理

- `GET /api/v1/admin/products`：名称关键词、分类、status、recommended、page、size 组合查询，默认更新时间倒序。记录为 `{product:商品卡片,status,recommended,recommendSort,version,updatedAt}`。
- `POST /api/v1/admin/products`：新增商品，默认下架。
- `GET /api/v1/admin/products/{id}`：商品、当前 images、retainedImages、全部规格及实物/预留/可售/阈值/库存版本。
- `PUT /api/v1/admin/products/{id}`：完整编辑，已有 SKU 带 id，库存字段只读，顶层带读取时的 version。
- `PATCH /api/v1/admin/products/{id}/status`：`{"status":1,"version":0}`，上架须有效启用二级分类及祖先、至少一张图及一个有效启用 SKU；零库存允许上架。
- `DELETE /api/v1/admin/products/{id}?version=...`：逻辑删除，有预留库存时拒绝，历史关联数据保留。

新增示例（imageIds 为实际已上传图片，categoryId 为实际二级分类）：

```json
{
  "name":"烟台红富士苹果","categoryId":2,
  "description":"清甜爽脆，独立包装。","origin":"山东烟台",
  "taste":"清甜爽脆","storageMethod":"建议冷藏",
  "recommended":true,"recommendSort":0,"imageIds":[1,2],
  "skus":[
    {"specification":"500g","price":"19.90","onHand":12,"status":1},
    {"specification":"1kg","price":"39.90","onHand":20,"status":1}
  ]
}
```

编辑增加顶层 version，已有 SKU 仅提交 id、specification、price、status。新 SKU 不带 ID，并提交初始 onHand，阈值默认 0；不再提交 adjustmentReason。不要把响应中的 reserved、availableStock、lowStock 等只读字段回传；请求额外字段被拒绝。价格范围 0.01..99999999.99、最多两位小数；SKU 最多 20 个，每个规格名唯一。规格停用或从请求移除后保留原 ID，可恢复；有预留量不得停用。已有规格名称可交换，标识不变。

可售量=实物 onHand−预留 reserved。商品只设置新 SKU 初始库存；已有 onHand、lowStockThreshold、reserved、inventoryVersion 不得回传，后续数量通过库存单据/盘点调整，阈值通过库存模块配置。普通商品信息保存不受库存版本变更影响，商品旧版本仍返回 `409 VERSION_CONFLICT`。库存作业保护预留并保存流水，详见 [库存 API](inventory-api.md)。

商品、SKU、库存、流水和成功审计同事务提交。管理日志记录操作者、操作类型、对象、时间和结果；失败日志只保存白名单错误元数据，不含 token、密码、完整请求体。本阶段无审计查询页面或 API。

## 图片

`POST /api/v1/admin/product-images` 是单文件 multipart，字段 file。接受真实 JPEG/PNG/WebP，单张最多 5 MiB，返回 `{"id":1,"url":"/api/v1/public/product-images/随机文件名.png"}`。GET 对应 URL 公开读取并设置 nosniff，不暴露目录或绝对路径。

每个商品最多 10 张，imageIds 顺序决定主图及图库。未绑定图片只能被上传者使用，不能绑定其他商品图片或任意外部 URL。移除已绑定图片后保留文件与元数据，确保历史收藏主图可读取；在“选用之前的图片”恢复。

`PRODUCT_IMAGE_STORAGE_DIR` 默认 `./uploads/products`，相对后端启动工作目录。备份数据库时同步备份图片目录，上传内容不提交 Git。取消编辑的未绑定上传可显式清理：

```bash
# 按 README 导出 DB_* 和 PRODUCT_IMAGE_STORAGE_DIR 后执行。
python3 scripts/cleanup-product-images.py
python3 scripts/cleanup-product-images.py --apply
```

默认只预览；--apply 删除超过 24 小时且仍未绑定的图片。条件删除防止与并发绑定冲突，已绑定和历史图片不清理。需要 MySQL CLI。

## 公开搜索与详情

- `GET /api/v1/public/products` 参数为 keyword、categoryId、minPrice、maxPrice、sort、page、size。
- `GET /api/v1/public/products/recommendations`：最多 8 件，人工推荐按顺序优先，不足时按最新上架补足且不重复。
- `GET /api/v1/public/products/{id}`：图库、分类路径、商品信息、启用规格及可售库存、评价摘要。

关键词匹配名称或描述，%/_ 按文本匹配。一级分类包括其有效二级分类。价格区间为闭区间，必须同一启用 SKU 同时命中两端；只填一端表示另一端不限。规格为 19.90/39.90 时，30..50 筛选仍命中，卡片展示 19.90 元起。sort 为 NEWEST、PRICE_ASC、PRICE_DESC；价格排序按全部启用 SKU 最低价，含售罄规格。

公开商品须上架、未删除、所属分类及祖先启用且有启用 SKU。售罄仍浏览/收藏；下架、删除、分类不可用的详情及评价统一 404。不存在或停用分类搜索返回空页；负价、超两位小数、倒置区间及非法分页/排序返回 400。普通商品编辑不更新上架时间，再次上架会更新。公开 DTO 不返回管理字段。

组合查询示例：`/api/v1/public/products?keyword=苹果&categoryId=1&minPrice=30.00&maxPrice=50.00&sort=PRICE_ASC&page=1&size=12`。商品与图片 ID 以实际数据为准，成功响应示例：

```json
{
  "code":"OK","message":"success",
  "data":{
    "items":[{"id":1,"name":"烟台红富士苹果","categoryId":2,"categoryName":"苹果","mainImage":"/api/v1/public/product-images/图片文件名.png","minPrice":"19.90","maxPrice":"39.90","availableStock":32,"soldOut":false}],
    "total":1,"page":1,"size":12
  }
}
```

推荐接口的 data 为相同卡片结构的数组。详情返回 name、categoryPath、images、description、origin、taste、storageMethod、skus 和 reviewSummary；每个公开规格为 `{"id":1,"specification":"500g","price":"19.90","availableStock":12}`，暂无评价为 `{"count":0,"averageRating":null}`。

## 收藏与只读评价

- `GET /api/v1/buyer/favorites?page=1`：本人收藏，收藏时间倒序。
- `GET /api/v1/buyer/favorites/{productId}`：`{favorited:true/false}`。
- `PUT /api/v1/buyer/favorites/{productId}`：收藏可见商品，重复/并发只一条。
- `DELETE /api/v1/buyer/favorites/{productId}`：取消本人收藏，商品失效或已取消仍成功。

买家身份从 JWT 读取。失效收藏返回收藏时名称/主图、available=false、原因和 product=null，不返回当前下架商品价格/SKU。原因 DELETED、UNLISTED、CATEGORY_DISABLED。售罄不是失效；重新上架自动恢复。游客点击收藏先登录，成功后返回详情，不自动重放收藏。

`GET /api/v1/public/products/{id}/reviews` 分页读取已发布评价。详情 reviewSummary 根据全部已发布评价计算条数和一位小数均分；无评价 count=0、averageRating=null。公开评价不含账号、手机号、订单标识，文字按普通文本展示。本阶段无提交、编辑、删除或管理员代写，订单完成后再补已购校验和写入。新库自然无评价，隔离测试夹具不进入日常数据库。

## 前后端验收

1. 管理员创建一级及二级分类，新增两张图片、两个不同价格库存的 SKU，保存后上架。
2. 设置推荐及顺序；游客首页查看推荐，按分类/关键词/价格搜索，详情切换图片/规格，核对库存和暂无评价。
3. 买家收藏并在个人中心查看；后台改价/库存，前台以最新读取值展示。
4. 两窗口读取同一商品并改库存，旧版本提交收到冲突，草稿保留。
5. 下架或停用分类后详情不可访问，收藏显示失效；重新上架/启用后恢复。
6. 删除商品，分类因历史关联不可删除；买家可取消失效收藏。
7. 手机和桌面都检查搜索、图库、规格编辑、分类和错误反馈。

常规验证：`mvn -f back-end/pom.xml test`、`npm --prefix front-end run typecheck`、`npm --prefix front-end run build`。

可选 MySQL 测试必须指向两个专用空库：CATALOG_TEST_DB_URL（业务集成）、CATALOG_MIGRATION_DB_URL（另一专用迁移库），并导出 CATALOG_TEST_DB_USERNAME / CATALOG_TEST_DB_PASSWORD。禁止指向日常数据。运行：

```bash
mvn -f back-end/pom.xml -Dtest=CatalogIntegrationTest,MySqlMigrationTest test
```

迁移测试先应用 V1/V2 写账户及地址，再应用 V3 并重复 migrate，验证数据保留、根分类唯一生成列及外键；未设置 MySQL 环境时业务测试用 H2，迁移测试跳过。
