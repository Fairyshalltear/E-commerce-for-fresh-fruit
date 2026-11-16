# 管理端审计日志 API

审计查询仅店主可用，管理 API 不提供编辑或手动删除日志的操作。系统每天 03:00（Asia/Shanghai）自动删除创建时间早于当前时间 7 天的日志；被库存流水引用的日志保留，以维护库存追溯和外键关系。`GET /api/v1/admin/audit-logs` 支持 `page`、`size`（最大 100）、`actorId`、`category`、`result`、`target`、`sourceIp`、`requestPath`、`from`、`to` 筛选，按创建时间及 ID 倒序稳定分页。`GET /api/v1/admin/audit-logs/{id}` 返回记录详情。

登录结果记录为 `AUTHENTICATION` 类别；管理写入及拒绝操作记录为 `BUSINESS` 类别，结果为 `SUCCESS`、`FAILED` 或 `DENIED`。日志保留账号名快照、请求路径、来源连接 IP、目标摘要和失败类别。默认不信任转发 IP 请求头。请求正文、密码、密码哈希、token 和 Authorization header 不进入日志。买家登录只保存聚合所需的买家 ID 与成功时间，不进入本管理日志，也不提供买家登录明细。

成功业务修改与审计记录处于同一事务；登录尝试、拒绝和未通过业务校验的管理写入在独立事务中保留。
