# Spec Delta

## Purpose

使单店店主能够安全地委派日常后台工作，同时保留对全部业务和权限配置的控制。每个授权都必须在服务端生效，并可追溯地变更，避免依赖界面隐藏实现安全边界。

## ADDED Requirements

### Requirement: Owner and staff roles have distinct authority
The system SHALL distinguish the shop owner from staff accounts. The owner SHALL have all management permissions, and only the owner SHALL create, disable, or change staff accounts and permissions.

#### Scenario: Owner uses a management module
- **WHEN** the owner requests any supported management module or operation
- **THEN** the request is authorized without requiring a staff permission assignment

#### Scenario: Staff attempts to manage staff authority
- **WHEN** a staff member requests staff account or permission administration
- **THEN** the system rejects the request regardless of the staff member's module permissions

### Requirement: Owner can assign staff module permissions
The system SHALL let the owner independently grant or revoke staff access to the product, order, inventory, after-sales, content, and statistics modules. The system SHALL enforce module access on each corresponding management API, not only on frontend navigation.

#### Scenario: Staff has a module permission
- **WHEN** an active staff member requests an API belonging to a granted module
- **THEN** the system permits the request subject to any additional operation permission

#### Scenario: Staff lacks a module permission
- **WHEN** an active staff member directly requests an API belonging to an ungranted module
- **THEN** the system rejects the request and performs no business mutation

### Requirement: High-impact business operations require separate staff permissions
The system SHALL support independent staff permission switches for refund execution, product publishing or unlisting, and inventory-changing operations. A staff member SHALL hold both the relevant module permission and the specific high-impact permission to perform a protected operation. Account and permission changes SHALL remain owner-only and SHALL NOT be delegated to staff.

#### Scenario: Staff has module and high-impact permission
- **WHEN** a staff member performs a protected operation with both required permissions
- **THEN** the system allows the operation and records its actor and result in the audit log

#### Scenario: Staff has only module permission
- **WHEN** a staff member attempts a protected operation without its separate permission
- **THEN** the system rejects the operation before mutation and records the denied attempt in the audit log

### Requirement: Permission changes affect subsequent requests promptly
The system SHALL apply permission grants, revocations, and staff disablement to subsequent API requests without relying solely on permissions cached in an unexpired token. Disabled staff SHALL NOT continue to access management APIs.

#### Scenario: Owner revokes a staff permission
- **WHEN** a staff permission is revoked while the staff member has an existing token
- **THEN** the next request requiring that permission is rejected

#### Scenario: Owner disables staff
- **WHEN** an owner disables a staff account
- **THEN** subsequent requests using that staff account's existing token are rejected

### Requirement: Staff account lifecycle is controlled and auditable
The system SHALL allow only the owner to create staff credentials, update staff display identity and permissions, disable or re-enable accounts, and reset credentials. Staff accounts SHALL NOT be hard-deleted, and staff permission or lifecycle changes SHALL record the owner, time, target, and result.

#### Scenario: Owner changes staff permissions
- **WHEN** an owner changes a staff member's module or high-impact permissions
- **THEN** the system saves the change and creates an audit record describing the changed permission set

#### Scenario: Staff attempts to change account permissions
- **WHEN** a staff member directly requests a staff account or permission change
- **THEN** the system rejects the request and creates an audit record of the denied attempt

#### Scenario: Staff account is disabled
- **WHEN** an owner disables a staff account
- **THEN** the account remains available for audit attribution but cannot authenticate or call management APIs

### Requirement: Existing administrator access is preserved as owner access
The system SHALL migrate the existing bootstrapped administrator account to the owner role and SHALL preserve its ability to sign in and manage all modules after the authorization model changes.

#### Scenario: Existing administrator signs in after upgrade
- **WHEN** the existing administrator authenticates after migration
- **THEN** the issued session identifies the owner role and grants all management permissions
