# Spec Delta

## Purpose

为买家和后台管理员提供受控的账号生命周期、身份验证与联系方式管理，使购物和后台操作都能关联到明确的账户。

## ADDED Requirements

### Requirement: Buyer registration and password login
系统 SHALL 允许买家使用用户名、手机号和密码注册，并允许使用用户名或手机号配合密码登录。用户名与手机号 SHALL 在买家账户中保持唯一，系统 SHALL 拒绝无效凭据和已禁用账户的登录。

#### Scenario: Buyer registers and signs in by phone
- **WHEN** 买家提交唯一用户名、手机号和有效密码并随后使用手机号登录
- **THEN** 系统创建买家账户并建立已认证会话

#### Scenario: Duplicate identity is rejected
- **WHEN** 买家提交已被其他账户使用的用户名或手机号
- **THEN** 系统拒绝注册并指出发生冲突的字段

### Requirement: Buyer profile and address management
系统 SHALL 允许已登录买家查看和修改昵称、头像与密码，并对收货地址执行新增、编辑、删除和默认地址设置。每个买家至多 SHALL 有一个默认地址。

#### Scenario: Buyer selects a default address
- **WHEN** 买家将一个已有地址设为默认地址
- **THEN** 系统将该地址设为默认并清除该买家其他地址的默认标记

#### Scenario: Buyer changes another account address
- **WHEN** 买家尝试读取或修改不属于自己的地址
- **THEN** 系统拒绝该操作且不泄露地址内容

### Requirement: Administrator manages buyer accounts
系统 SHALL 允许已认证管理员分页查询买家、查看账户详情、启用或禁用账户并重置买家密码。禁用账户 SHALL 无法建立新的买家登录会话。

#### Scenario: Administrator disables a buyer
- **WHEN** 管理员禁用一个买家账户且该买家随后尝试登录
- **THEN** 系统拒绝登录并保留该账户已有的订单记录

### Requirement: Administrator accounts are provisioned and managed privately
系统 SHALL 通过本地部署配置初始化首个管理员账户，并只允许已认证管理员创建、禁用或重置其他管理员账户。系统 SHALL 不提供公开的管理员注册入口，也 SHALL 不将管理员密码或初始化凭据写入版本库。

#### Scenario: First administrator is provisioned
- **WHEN** 系统在尚无管理员账户的空数据库上完成首次初始化
- **THEN** 系统使用本地提供的初始化配置建立管理员账户

#### Scenario: Buyer cannot create an administrator
- **WHEN** 未认证访客或买家请求创建管理员账户
- **THEN** 系统拒绝该请求且不创建账户
