# Spec Delta

## Purpose

让已登录买家能够维护自己的联系资料、账户安全设置和常用收货地址，为商城购物流程提供可管理且受账户归属保护的买家信息。

## ADDED Requirements

### Requirement: Buyer views and updates personal profile
系统 SHALL 允许已登录买家查看和更新自己的昵称及手机号，并 SHALL 保持用户名不可修改。手机号 SHALL 符合系统手机号格式且在买家账户中唯一；本能力不要求短信验证码。

#### Scenario: Buyer updates profile
- **WHEN** 买家提交有效的新昵称或未被占用的手机号
- **THEN** 系统保存变更并返回更新后的个人资料

#### Scenario: Buyer submits a duplicate phone number
- **WHEN** 买家将手机号修改为另一买家已使用的号码
- **THEN** 系统拒绝更新并指出手机号冲突，原资料保持不变

#### Scenario: Buyer attempts to change username
- **WHEN** 买家提交用户名修改
- **THEN** 系统拒绝该字段变更且原用户名保持不变

### Requirement: Buyer uploads a profile avatar
系统 SHALL 允许已登录买家上传 JPEG、PNG 或 WebP 格式的头像图片，单个文件不得超过 5 MiB。系统 SHALL 拒绝不支持的格式或超限文件，且不得将头像文件内容作为可执行内容处理。

#### Scenario: Buyer uploads an accepted avatar
- **WHEN** 买家上传不超过 5 MiB 的 JPEG、PNG 或 WebP 图片
- **THEN** 系统保存头像并返回可用于个人资料展示的头像引用

#### Scenario: Buyer uploads an invalid avatar
- **WHEN** 买家上传不支持的文件格式或超过 5 MiB 的文件
- **THEN** 系统拒绝上传并返回可识别的验证错误

### Requirement: Buyer changes password
系统 SHALL 要求买家提供当前密码以设置新密码，并 SHALL 在当前密码错误或新密码不符合密码策略时拒绝变更。密码变更成功后，客户端 SHALL 清除当前登录状态并要求买家重新登录。

#### Scenario: Buyer changes password successfully
- **WHEN** 买家提供正确的当前密码和符合策略的新密码
- **THEN** 系统更新密码凭据，客户端结束当前登录状态并引导重新登录

#### Scenario: Buyer provides an incorrect current password
- **WHEN** 买家提供错误的当前密码
- **THEN** 系统拒绝变更且原密码仍然有效

### Requirement: Buyer manages delivery addresses
系统 SHALL 允许已登录买家新增、查看、编辑和删除自己的收货地址。地址 SHALL 包含收货人、联系电话、省、市、区县、详细地址和“家”或“公司”标签，不包含邮政编码；每位买家最多 SHALL 保存 5 个地址。

#### Scenario: Buyer creates an address below the limit
- **WHEN** 买家提交字段完整且有效的地址，且其当前地址数少于 5 个
- **THEN** 系统保存该地址并仅向该买家提供其内容

#### Scenario: Buyer reaches the address limit
- **WHEN** 买家已有 5 个地址并尝试新增另一个地址
- **THEN** 系统拒绝新增并说明每位买家最多可保存 5 个地址

#### Scenario: Buyer edits or deletes an owned address
- **WHEN** 买家修改或删除属于自己的地址
- **THEN** 系统只更新或删除该地址，并返回操作结果

### Requirement: Buyer controls the default delivery address
系统 SHALL 允许买家将自己的一个地址设为默认地址，并 SHALL 保证每位买家至多有一个默认地址。删除默认地址后，系统 SHALL 不将其他地址自动设为默认。

#### Scenario: Buyer sets a default address
- **WHEN** 买家将自己的一个地址设为默认地址
- **THEN** 该地址成为默认地址且该买家的其他地址均不再是默认地址

#### Scenario: Buyer deletes the default address
- **WHEN** 买家删除当前默认地址
- **THEN** 该地址被删除且其余地址均不被自动设为默认

### Requirement: Buyer address operations enforce ownership
系统 SHALL 根据已认证买家身份限定地址查询和修改范围，不得信任请求中提供的买家 ID。买家尝试操作他人地址时，系统 SHALL 拒绝请求且不得泄露该地址内容。

#### Scenario: Buyer attempts to access another buyer's address
- **WHEN** 买家读取、编辑、删除或设置他人地址为默认地址
- **THEN** 系统拒绝该操作且不返回地址内容
