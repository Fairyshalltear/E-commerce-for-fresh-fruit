# 商城轮播图与公告 API

管理接口需要店主或 `CONTENT` 模块店员权限。轮播图使用已校验图片接口返回的 `imageId`，跳转目标仅允许站内路径或带主机名的 HTTP/HTTPS URL。内容开始时间必填，结束时间必须晚于开始时间；时间按 `Asia/Shanghai` 解释。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `GET` / `POST` | `/api/v1/admin/content/carousels` | 列表 / 新建轮播图 |
| `PUT` | `/api/v1/admin/content/carousels/{id}` | 编辑、排序、启停及发布时间 |
| `GET` / `POST` | `/api/v1/admin/content/announcements` | 列表 / 新建公告 |
| `PUT` | `/api/v1/admin/content/announcements/{id}` | 编辑正文、置顶、排序、启停及发布时间 |
| `GET` | `/api/v1/public/content/carousels` | 读取当前有效轮播图 |
| `GET` | `/api/v1/public/content/announcements` | 读取当前公告摘要 |
| `GET` | `/api/v1/public/content/announcements/{id}` | 读取当前有效公告正文；草稿统一返回 404 |

公开内容必须处于启用状态，当前时间需满足 `startAt <= now < endAt`（无结束时间则仅检查开始时间）。公告置顶项优先，然后按排序值和 ID 稳定排序。新增、编辑与发布属性变化均留存操作审计。
