# Spec Delta

## Purpose

让管理员维护商城首页的轮播内容和公告，并让买家看到当前已发布的信息，满足平台基础运营展示需要。

## ADDED Requirements

### Requirement: Administrator manages homepage banners
系统 SHALL 允许管理员新增、编辑、排序、启用和停用首页轮播图，并记录图片及可选跳转目标。商城首页 SHALL 只展示已启用的轮播图并遵循管理员设置的顺序。

#### Scenario: Buyer views homepage banners
- **WHEN** 买家打开商城首页
- **THEN** 系统按配置顺序返回当前启用的轮播图

### Requirement: Administrator publishes announcements
系统 SHALL 允许管理员创建、编辑、发布和撤回公告，并保存标题、正文和发布时间。商城端 SHALL 只展示已发布且未撤回的公告。

#### Scenario: Buyer views a published announcement
- **WHEN** 买家打开公告列表或公告详情
- **THEN** 系统展示已发布公告的标题、正文和发布时间
