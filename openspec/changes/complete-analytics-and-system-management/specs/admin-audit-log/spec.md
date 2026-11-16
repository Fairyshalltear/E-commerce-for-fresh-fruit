# Spec Delta

## Purpose

为单店后台提供可查询且不可篡改的管理审计记录，帮助店主追溯店主和店员的登录、权限变更及业务操作。审计数据需保留必要的责任与请求上下文，同时避免保存认证秘密或不必要的敏感内容。

## ADDED Requirements

### Requirement: System records owner and staff authentication attempts
The system SHALL append a log entry for every owner or staff authentication attempt, including account identity when known, timestamp, source IP, result, and failure category when applicable. Authentication logs SHALL NOT contain passwords, password hashes, tokens, or full credential payloads.

#### Scenario: Authentication succeeds
- **WHEN** an owner or staff member successfully signs in
- **THEN** the system records a successful authentication event with account, time, and source IP

#### Scenario: Authentication fails
- **WHEN** an owner or staff authentication attempt fails
- **THEN** the system records a failed event and safe failure category without storing submitted credentials

### Requirement: System records management write operations
The system SHALL append an audit entry for every management write operation, including actor, timestamp, source IP, request path, operation, target summary, and success or failure result. Permission changes and high-impact operations SHALL be identifiable and searchable.

#### Scenario: Management operation succeeds
- **WHEN** an owner or authorized staff member completes a management write operation
- **THEN** the system records the actor, operation, target, request context, and successful result

#### Scenario: High-impact operation is denied
- **WHEN** a staff member is denied a high-impact operation for missing permission
- **THEN** the system records the actor, attempted operation, target when known, and denied result without applying the mutation

### Requirement: Audit records are immutable through management APIs and owner-readable
The system SHALL expose no management operation that edits or manually deletes authentication or business audit records. The system SHALL automatically delete audit records older than seven days once per day, except records referenced by inventory movements, which SHALL be retained to preserve traceability and referential integrity. Only the owner may read audit records; staff module permissions SHALL NOT grant access to system logs.

#### Scenario: Owner queries audit records
- **WHEN** the owner requests audit records
- **THEN** the system returns matching retained records without allowing manual changes to them

#### Scenario: Scheduled retention cleanup runs
- **WHEN** the daily cleanup runs at 03:00 Asia/Shanghai
- **THEN** it deletes records strictly older than seven days unless an inventory movement references them

#### Scenario: Staff queries audit records
- **WHEN** any staff member directly requests an audit API
- **THEN** the system denies access without disclosing log contents

### Requirement: Audit search is paginated and filterable
The system SHALL provide stable paginated audit search by actor, event category, result, target, and time range, and SHALL allow authorized users to inspect a single record's stored details.

#### Scenario: Query a matching time range
- **WHEN** an authorized user requests a valid filtered page
- **THEN** records are returned in deterministic newest-first order with pagination metadata

#### Scenario: Query an empty range
- **WHEN** a valid filter has no matching records
- **THEN** the system returns an empty page and does not substitute unrelated records

### Requirement: Audit record storage excludes authentication secrets
The system SHALL redact or omit passwords, password hashes, bearer tokens, authorization headers, and equivalent authentication secrets from audit fields and detail responses.

#### Scenario: Sensitive request fails
- **WHEN** a management request contains authentication secrets and is audited
- **THEN** no secret value is persisted or returned in the audit record
