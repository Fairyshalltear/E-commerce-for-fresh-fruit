# Spec Delta

## Purpose

让店主和获授权店员在后台维护商城首页的轮播图与公告，并确保访客只看到已启用且处于发布时间范围内的内容。内容管理能力覆盖编辑、排序和发布生命周期，不要求访客登录。

## ADDED Requirements

### Requirement: Authorized content staff can manage carousel items
The system SHALL allow users with content-module permission to create, view, update, reorder, enable, disable, and schedule carousel items containing an image, optional title, optional destination, and publication window. Content management APIs SHALL enforce the content permission server-side.

#### Scenario: Authorized user saves a carousel item
- **WHEN** an owner or staff member with content permission submits a valid carousel item
- **THEN** the system saves its image, optional title and destination, sort order, enabled state, and publication window

#### Scenario: Staff without content permission manages carousel items
- **WHEN** a staff member without content permission directly requests a content-management API
- **THEN** the system rejects the request and makes no content change

### Requirement: Authorized content staff can manage announcements
The system SHALL allow users with content-module permission to create, view, update, reorder, pin, publish, unpublish, and schedule announcements containing a title and body. Content management APIs SHALL enforce the content permission server-side.

#### Scenario: Authorized user publishes an announcement
- **WHEN** an owner or staff member with content permission saves an announcement with a publication window
- **THEN** the system stores the announcement and exposes it publicly only while its publication conditions are met

#### Scenario: Announcement is unpublished
- **WHEN** an authorized user disables or unpublishes an announcement
- **THEN** the system stops returning it from public announcement APIs

### Requirement: Public carousel content is restricted to currently published items
The system SHALL expose enabled carousel items whose publication start is at or before the current time and whose optional end is after the current time, ordered by configured sort order and a stable identifier. Public responses SHALL omit drafts, disabled, future, and expired items.

#### Scenario: Carousel item is within its publication window
- **WHEN** a visitor requests homepage carousel content while an item is enabled and within its publication window
- **THEN** the item is returned in its configured order

#### Scenario: Carousel item is not currently published
- **WHEN** a carousel item is disabled, scheduled for the future, or expired
- **THEN** it is omitted from the public response

### Requirement: Public announcements expose published summaries and details
The system SHALL expose currently published announcements as summaries and provide a public detail view for a published announcement. Pinned announcements SHALL precede unpinned announcements, with configured order and a stable identifier resolving ties. Draft, disabled, future, and expired announcements SHALL NOT be publicly readable.

#### Scenario: Visitor lists current announcements
- **WHEN** a visitor requests the public announcement list
- **THEN** the system returns only current published announcements with pinned items first

#### Scenario: Visitor requests an unpublished announcement
- **WHEN** a visitor requests a draft, disabled, future, expired, or nonexistent announcement by identifier
- **THEN** the system returns not found without exposing its content

### Requirement: Content publication windows use a consistent timezone
The system SHALL interpret content publication times in Asia/Shanghai and SHALL reject a publication window whose end is not later than its start.

#### Scenario: Valid scheduled content becomes public
- **WHEN** the current Asia/Shanghai time reaches an enabled item's start time and precedes its end time, if any
- **THEN** the public API includes that item without requiring a manual status transition at the scheduled time

#### Scenario: Invalid publication window is submitted
- **WHEN** content is submitted with an end time equal to or earlier than its start time
- **THEN** the system rejects the request with a validation error
