# Spec Delta

## Purpose

为单店店主提供可信、可筛选的经营数据总览，帮助比较销售、订单、买家和商品表现。统计结果须能由现有业务记录复核，并在前后端使用一致的时间与退款口径。

## ADDED Requirements

### Requirement: Authorized staff can query business statistics
The system SHALL provide an authenticated management statistics view for sales, orders, buyers, and products, and SHALL enforce the caller's statistics-module permission on every statistics API.

#### Scenario: Staff has statistics permission
- **WHEN** an authenticated staff member with statistics permission requests a supported statistics query
- **THEN** the system returns the requested aggregate data

#### Scenario: Staff lacks statistics permission
- **WHEN** an authenticated staff member without statistics permission requests a statistics API directly
- **THEN** the system rejects the request without returning aggregate data

### Requirement: Statistics queries use supported periods and timezone
The system SHALL support today, current week, current month, trailing 30 calendar days, and a custom inclusive date range, with daily, Monday-start weekly, or monthly grouping. Date boundaries SHALL be interpreted in Asia/Shanghai, and invalid or reversed ranges SHALL be rejected.

#### Scenario: Query a supported period
- **WHEN** a user selects a supported preset or valid custom range and grouping
- **THEN** the system returns values and trend buckets for that range using Asia/Shanghai calendar boundaries

#### Scenario: Query an invalid range
- **WHEN** the start date is after the end date, a date value is invalid, or the grouping is unsupported
- **THEN** the system returns a validation error and no partial aggregate

### Requirement: Sales statistics net successful payment and refund events
The system SHALL count successful payment amounts and paid item quantities as positive sales events, and successful refund amounts and refunded item quantities as negative events. Unpaid, failed, and cancelled payment attempts SHALL NOT count as sales. Trend buckets SHALL attribute each payment or refund to its recorded event date.

#### Scenario: Successful payment and refund are included
- **WHEN** the selected range includes a successful payment and a successful refund
- **THEN** sales amount equals successful payment amount minus successful refund amount, and sales quantity equals paid quantity minus refunded quantity

#### Scenario: Non-successful payment is excluded
- **WHEN** an order has only pending, failed, or cancelled payment attempts
- **THEN** its amount and item quantities do not increase sales statistics

### Requirement: Order statistics report created orders and current status distribution
The system SHALL report order volume by order creation time and SHALL report the status distribution of orders created within the selected range using each order's current status.

#### Scenario: Query order statistics
- **WHEN** a valid range is requested
- **THEN** the result includes total created order count, a count for every supported order status, and trend buckets whose counts reconcile to the total

### Requirement: Buyer statistics report registrations and active buyers
The system SHALL report buyer registrations by account creation time and active buyers as the distinct union of buyers with a successful login or at least one created order during the selected range.

#### Scenario: Buyer logs in and places orders
- **WHEN** the same buyer has one or more successful logins and orders within the selected range
- **THEN** the buyer contributes exactly one active buyer to the total

#### Scenario: Buyer activity is outside the selected range
- **WHEN** a buyer's successful logins and order creation times are all outside the selected range
- **THEN** that buyer is not included in active buyers for the range

### Requirement: Product rankings expose sales and inventory measures
The system SHALL provide a best-selling products ranking showing net sold quantity and net sales amount, defaulting to descending net sold quantity, and an inventory ranking defaulting to descending available quantity. Ties SHALL use a stable deterministic secondary ordering, and deleted products SHALL be excluded from both rankings.

#### Scenario: Query product rankings
- **WHEN** a user requests rankings for a valid period and ranking limit
- **THEN** the best-selling list is ordered by net sold quantity and exposes net sales amount, while the inventory list is ordered by current available quantity

#### Scenario: Ranking values are tied
- **WHEN** two products have equal primary ranking values
- **THEN** the system orders them deterministically by product identifier

### Requirement: Statistics responses identify empty and invalid results
The system SHALL return zero-valued aggregates and empty trend/ranking collections when a valid query has no matching business records, and SHALL NOT fabricate missing or unavailable measurements.

#### Scenario: Valid range has no data
- **WHEN** a valid range contains no matching orders, payments, logins, or product sales
- **THEN** the system returns zero aggregates and empty applicable collections
