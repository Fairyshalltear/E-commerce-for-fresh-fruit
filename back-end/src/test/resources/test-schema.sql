CREATE TABLE buyer_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    phone VARCHAR(20) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    nickname VARCHAR(50),
    avatar_url VARCHAR(500),
    status INTEGER NOT NULL DEFAULT 1,
    credential_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE admin_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(50),
    status INTEGER NOT NULL DEFAULT 1,
    account_type VARCHAR(20) NOT NULL DEFAULT 'OWNER' CHECK (account_type IN ('OWNER','STAFF')),
    credential_version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE admin_permission (
 admin_id BIGINT NOT NULL, permission_scope VARCHAR(20) NOT NULL, permission_key VARCHAR(40) NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(admin_id,permission_scope,permission_key), FOREIGN KEY(admin_id) REFERENCES admin_account(id),
 CHECK(permission_scope IN ('MODULE','OPERATION')),
 CHECK(permission_key IN ('PRODUCTS','BUYERS','ORDERS','INVENTORY','AFTER_SALES','CONTENT','STATISTICS','REFUND_EXECUTE','PRODUCT_STATUS_CHANGE','INVENTORY_ADJUST'))
);
CREATE INDEX idx_admin_permission_lookup ON admin_permission(permission_scope,permission_key,admin_id);

CREATE TABLE user_address (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    buyer_id BIGINT NOT NULL,
    recipient_name VARCHAR(50) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    province VARCHAR(50) NOT NULL,
    city VARCHAR(50) NOT NULL,
    district VARCHAR(50) NOT NULL,
    detail_address VARCHAR(200) NOT NULL,
    address_tag VARCHAR(20) NOT NULL CHECK (address_tag IN ('HOME', 'COMPANY')),
    is_default INTEGER NOT NULL DEFAULT 0 CHECK (is_default IN (0, 1)),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (buyer_id) REFERENCES buyer_account (id) ON DELETE CASCADE
);

CREATE TABLE product_category (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 parent_id BIGINT NULL,
 parent_scope BIGINT GENERATED ALWAYS AS (COALESCE(parent_id, 0)),
 name VARCHAR(50) NOT NULL, sort_order INT NOT NULL DEFAULT 0, status INT NOT NULL DEFAULT 1,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_category_sibling UNIQUE (parent_scope, name),
 FOREIGN KEY (parent_id) REFERENCES product_category(id),
 CHECK (status IN (0,1)), CHECK (sort_order >= 0)
);
CREATE TABLE fruit_product (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, category_id BIGINT NOT NULL, name VARCHAR(100) NOT NULL,
 description VARCHAR(5000) NOT NULL DEFAULT '', origin VARCHAR(500) NOT NULL DEFAULT '',
 taste VARCHAR(500) NOT NULL DEFAULT '', storage_method VARCHAR(500) NOT NULL DEFAULT '',
 status INT NOT NULL DEFAULT 0, is_recommended INT NOT NULL DEFAULT 0, recommend_sort INT NOT NULL DEFAULT 0,
 published_at DATETIME NULL, deleted INT NOT NULL DEFAULT 0, version BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(category_id) REFERENCES product_category(id),
 CHECK(status IN (0,1)), CHECK(deleted IN (0,1)), CHECK(is_recommended IN (0,1)), CHECK(recommend_sort >= 0)
);
CREATE INDEX idx_product_catalog ON fruit_product(category_id,status,deleted,published_at,id);
CREATE TABLE product_sku (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, product_id BIGINT NOT NULL, specification VARCHAR(100) NOT NULL,
 price DECIMAL(10,2) NOT NULL, status INT NOT NULL DEFAULT 1, sort_order INT NOT NULL DEFAULT 0,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_product_specification UNIQUE(product_id,specification), FOREIGN KEY(product_id) REFERENCES fruit_product(id),
 CHECK(price > 0), CHECK(status IN (0,1)), CHECK(sort_order >= 0)
);
CREATE INDEX idx_sku_product_price ON product_sku(product_id,status,price);
CREATE TABLE product_image (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, storage_key VARCHAR(100) NOT NULL UNIQUE,
 uploaded_by BIGINT NOT NULL, product_id BIGINT NULL, active INT NOT NULL DEFAULT 0, sort_order INT NOT NULL DEFAULT 0,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(uploaded_by) REFERENCES admin_account(id), FOREIGN KEY(product_id) REFERENCES fruit_product(id), CHECK(active IN (0,1))
);
CREATE INDEX idx_image_product ON product_image(product_id,active,sort_order);
CREATE TABLE sku_inventory (
 sku_id BIGINT PRIMARY KEY, on_hand INT NOT NULL DEFAULT 0, reserved INT NOT NULL DEFAULT 0,
 low_stock_threshold INT NOT NULL DEFAULT 0, version BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(sku_id) REFERENCES product_sku(id), CHECK(on_hand >= reserved), CHECK(reserved >= 0), CHECK(low_stock_threshold >= 0)
);
CREATE TABLE admin_operation_log (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, admin_id BIGINT,
 operation_type VARCHAR(50) NOT NULL, target_type VARCHAR(30), target_id BIGINT,
 result VARCHAR(20) NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 event_category VARCHAR(30), actor_username_snapshot VARCHAR(50), source_ip VARCHAR(45), request_path VARCHAR(255),
 target_summary VARCHAR(500), failure_category VARCHAR(80),
 FOREIGN KEY(admin_id) REFERENCES admin_account(id)
);
CREATE INDEX idx_admin_log_actor_time ON admin_operation_log(admin_id,created_at,id);
CREATE INDEX idx_admin_log_operation_time ON admin_operation_log(operation_type,created_at,id);
CREATE INDEX idx_admin_log_result_time ON admin_operation_log(result,created_at,id);
CREATE INDEX idx_admin_log_retention ON admin_operation_log(created_at,id);
CREATE TABLE buyer_login_activity (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, buyer_id BIGINT NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(buyer_id) REFERENCES buyer_account(id)
);
CREATE INDEX idx_buyer_login_activity_time_buyer ON buyer_login_activity(created_at,buyer_id);
CREATE TABLE inventory_movement (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, sku_id BIGINT NOT NULL, operation_log_id BIGINT NOT NULL,
 type VARCHAR(40) NOT NULL, before_on_hand INT NOT NULL, after_on_hand INT NOT NULL, quantity_delta INT NOT NULL,
 reason VARCHAR(200) NOT NULL, admin_id BIGINT NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(sku_id) REFERENCES product_sku(id), FOREIGN KEY(operation_log_id) REFERENCES admin_operation_log(id),
 FOREIGN KEY(admin_id) REFERENCES admin_account(id), CHECK(after_on_hand >= 0), CHECK(before_on_hand >= 0)
);
CREATE INDEX idx_movement_sku_time ON inventory_movement(sku_id,created_at,id);
CREATE INDEX idx_buyer_created_at ON buyer_account(created_at,id);
CREATE TABLE user_favorite (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, buyer_id BIGINT NOT NULL, product_id BIGINT NOT NULL,
 product_name_snapshot VARCHAR(100) NOT NULL, main_image_snapshot VARCHAR(300) NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_favorite_owner_product UNIQUE(buyer_id,product_id),
 FOREIGN KEY(buyer_id) REFERENCES buyer_account(id), FOREIGN KEY(product_id) REFERENCES fruit_product(id)
);
CREATE INDEX idx_favorite_buyer_time ON user_favorite(buyer_id,created_at,id);
CREATE TABLE product_review (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, product_id BIGINT NOT NULL, sku_id BIGINT NOT NULL, buyer_id BIGINT NOT NULL,
 order_item_id BIGINT NOT NULL UNIQUE, rating INT NOT NULL, content VARCHAR(2000) NOT NULL DEFAULT '',
 specification_snapshot VARCHAR(100) NOT NULL, status INT NOT NULL DEFAULT 1, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(product_id) REFERENCES fruit_product(id), FOREIGN KEY(sku_id) REFERENCES product_sku(id),
 FOREIGN KEY(buyer_id) REFERENCES buyer_account(id), CHECK(rating BETWEEN 1 AND 5), CHECK(status IN (0,1))
);
CREATE INDEX idx_review_product_time ON product_review(product_id,status,created_at,id);
CREATE TABLE cart_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    buyer_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    selected INTEGER NOT NULL DEFAULT 1 CHECK (selected IN (0, 1)),
    accepted_unit_price DECIMAL(10,2) NOT NULL CHECK (accepted_unit_price > 0),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cart_item_buyer_sku UNIQUE (buyer_id, sku_id),
    FOREIGN KEY (buyer_id) REFERENCES buyer_account (id) ON DELETE CASCADE,
    FOREIGN KEY (sku_id) REFERENCES product_sku (id)
);
CREATE INDEX idx_cart_item_buyer_created ON cart_item(buyer_id, created_at, id);

CREATE TABLE shop_order (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, order_no VARCHAR(40) NOT NULL UNIQUE, buyer_id BIGINT NOT NULL,
 idempotency_key VARCHAR(80) NOT NULL, status VARCHAR(20) NOT NULL,
 subtotal DECIMAL(10,2) NOT NULL, shipping_fee DECIMAL(10,2) NOT NULL DEFAULT 0,
 total_amount DECIMAL(10,2) NOT NULL, refunded_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
 recipient_name VARCHAR(50) NOT NULL, phone VARCHAR(20) NOT NULL, province VARCHAR(50) NOT NULL,
 city VARCHAR(50) NOT NULL, district VARCHAR(50) NOT NULL, detail_address VARCHAR(200) NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, paid_at DATETIME NULL, shipped_at DATETIME NULL,
 completed_at DATETIME NULL, cancelled_at DATETIME NULL,
 CONSTRAINT uk_order_buyer_request UNIQUE (buyer_id,idempotency_key),
 FOREIGN KEY (buyer_id) REFERENCES buyer_account(id),
 CHECK (status IN ('PENDING_PAYMENT','PENDING_SHIPMENT','PENDING_RECEIPT','COMPLETED','CANCELLED')),
 CHECK (subtotal >= 0 AND shipping_fee >= 0 AND total_amount >= 0 AND refunded_amount >= 0)
);
CREATE INDEX idx_order_buyer_status_time ON shop_order(buyer_id,status,created_at,id);
CREATE INDEX idx_order_status_time ON shop_order(status,created_at,id);
CREATE INDEX idx_order_created_buyer_status ON shop_order(created_at,buyer_id,status,id);
CREATE TABLE order_item (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, product_id BIGINT NOT NULL, sku_id BIGINT NOT NULL,
 product_name_snapshot VARCHAR(100) NOT NULL, specification_snapshot VARCHAR(100) NOT NULL, image_snapshot VARCHAR(300),
 unit_price DECIMAL(10,2) NOT NULL, quantity INT NOT NULL, shipped_quantity INT NOT NULL DEFAULT 0,
 refund_pending_quantity INT NOT NULL DEFAULT 0, refunded_quantity INT NOT NULL DEFAULT 0,
 restocked_quantity INT NOT NULL DEFAULT 0, line_amount DECIMAL(10,2) NOT NULL,
 FOREIGN KEY (order_id) REFERENCES shop_order(id), FOREIGN KEY (product_id) REFERENCES fruit_product(id),
 FOREIGN KEY (sku_id) REFERENCES product_sku(id),
 CHECK (quantity > 0 AND shipped_quantity >= 0 AND refund_pending_quantity >= 0 AND refunded_quantity >= 0 AND restocked_quantity >= 0),
 CHECK (unit_price > 0 AND line_amount >= 0)
);
CREATE INDEX idx_order_item_order ON order_item(order_id,id);
CREATE INDEX idx_order_item_sku ON order_item(sku_id,order_id);
CREATE TABLE payment_record (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, idempotency_key VARCHAR(80) NOT NULL,
 channel VARCHAR(20) NOT NULL, result VARCHAR(20) NOT NULL, amount DECIMAL(10,2) NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_payment_order_request UNIQUE (order_id,idempotency_key),
 FOREIGN KEY (order_id) REFERENCES shop_order(id), CHECK (channel IN ('ALIPAY','WECHAT')),
 CHECK (result IN ('SUCCESS','FAILED','CANCELLED')), CHECK (amount >= 0)
);
CREATE INDEX idx_payment_order_time ON payment_record(order_id,created_at,id);
CREATE INDEX idx_payment_result_time ON payment_record(result,created_at,order_id,id);
CREATE TABLE shipment (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL UNIQUE, carrier VARCHAR(80) NOT NULL,
 tracking_number VARCHAR(100) NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (order_id) REFERENCES shop_order(id)
);
CREATE TABLE order_status_event (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, from_status VARCHAR(20), to_status VARCHAR(20) NOT NULL,
 event_type VARCHAR(40) NOT NULL, actor_type VARCHAR(20) NOT NULL, actor_id BIGINT, note VARCHAR(500),
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (order_id) REFERENCES shop_order(id)
);
CREATE INDEX idx_order_event_time ON order_status_event(order_id,created_at,id);
CREATE TABLE shipment_event (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, shipment_id BIGINT NOT NULL, description VARCHAR(500) NOT NULL,
 occurred_at DATETIME NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (shipment_id) REFERENCES shipment(id)
);
CREATE INDEX idx_shipment_event_time ON shipment_event(shipment_id,occurred_at,id);
CREATE TABLE after_sale_request (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, request_no VARCHAR(40) NOT NULL UNIQUE, order_id BIGINT NOT NULL,
 buyer_id BIGINT NOT NULL, type VARCHAR(20) NOT NULL, status VARCHAR(30) NOT NULL, reason VARCHAR(100) NOT NULL,
 description VARCHAR(1000) NOT NULL DEFAULT '', requested_amount DECIMAL(10,2) NOT NULL, review_reason VARCHAR(500),
 reviewed_by BIGINT, reviewed_at DATETIME, return_carrier VARCHAR(80), return_tracking_number VARCHAR(100),
 received_at DATETIME, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (order_id) REFERENCES shop_order(id), FOREIGN KEY (buyer_id) REFERENCES buyer_account(id),
 FOREIGN KEY (reviewed_by) REFERENCES admin_account(id), CHECK (type IN ('REFUND','RETURN')),
 CHECK (status IN ('PENDING_REVIEW','WAITING_RETURN','WAITING_RECEIPT','COMPLETED','REJECTED')),
 CHECK (requested_amount >= 0)
);
CREATE INDEX idx_after_sale_status_time ON after_sale_request(status,created_at,id);
CREATE INDEX idx_after_sale_order ON after_sale_request(order_id,buyer_id,created_at);
CREATE TABLE after_sale_item (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, request_id BIGINT NOT NULL, order_item_id BIGINT NOT NULL,
 quantity INT NOT NULL, refund_quantity INT NOT NULL DEFAULT 0, restock_quantity INT NOT NULL DEFAULT 0,
 unit_price DECIMAL(10,2) NOT NULL, CONSTRAINT uk_after_sale_request_item UNIQUE(request_id,order_item_id),
 FOREIGN KEY (request_id) REFERENCES after_sale_request(id), FOREIGN KEY (order_item_id) REFERENCES order_item(id),
 CHECK (quantity > 0 AND refund_quantity >= 0 AND restock_quantity >= 0 AND refund_quantity <= quantity AND restock_quantity <= quantity),
 CHECK (unit_price > 0)
);
CREATE INDEX idx_after_sale_item_order ON after_sale_item(order_item_id,request_id);
CREATE TABLE refund_record (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, request_id BIGINT NOT NULL UNIQUE, order_id BIGINT NOT NULL,
 amount DECIMAL(10,2) NOT NULL, result VARCHAR(20) NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (request_id) REFERENCES after_sale_request(id), FOREIGN KEY (order_id) REFERENCES shop_order(id),
 CHECK (amount >= 0), CHECK (result = 'SIMULATED_SUCCESS')
);
CREATE INDEX idx_refund_order_time ON refund_record(order_id,created_at,id);
CREATE INDEX idx_refund_result_time ON refund_record(result,created_at,order_id,id);
ALTER TABLE product_review ADD COLUMN deleted_at DATETIME NULL;
ALTER TABLE product_review ADD COLUMN deleted_by BIGINT NULL;
ALTER TABLE product_review ADD COLUMN deletion_reason VARCHAR(500) NULL;
ALTER TABLE product_review ADD CONSTRAINT fk_review_deleted_by FOREIGN KEY (deleted_by) REFERENCES admin_account(id);
CREATE TABLE product_review_deletion_audit (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, review_id BIGINT NOT NULL, admin_id BIGINT NOT NULL,
 reason VARCHAR(500) NOT NULL, review_snapshot CLOB NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (review_id) REFERENCES product_review(id), FOREIGN KEY (admin_id) REFERENCES admin_account(id)
);
CREATE INDEX idx_review_delete_audit ON product_review_deletion_audit(review_id,created_at,id);

CREATE TABLE storefront_carousel (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, image_id BIGINT NOT NULL, title VARCHAR(100), target_url VARCHAR(500),
 sort_order INT NOT NULL DEFAULT 0 CHECK(sort_order >= 0), enabled INTEGER NOT NULL DEFAULT 0 CHECK(enabled IN (0,1)),
 start_at DATETIME NOT NULL, end_at DATETIME, created_by BIGINT NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(image_id) REFERENCES product_image(id), FOREIGN KEY(created_by) REFERENCES admin_account(id),
 CHECK(end_at IS NULL OR end_at > start_at)
);
CREATE INDEX idx_storefront_carousel_public ON storefront_carousel(enabled,start_at,end_at,sort_order,id);
CREATE TABLE storefront_announcement (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(150) NOT NULL, summary VARCHAR(500) NOT NULL DEFAULT '', body CLOB NOT NULL,
 sort_order INT NOT NULL DEFAULT 0 CHECK(sort_order >= 0), pinned INTEGER NOT NULL DEFAULT 0 CHECK(pinned IN (0,1)),
 enabled INTEGER NOT NULL DEFAULT 0 CHECK(enabled IN (0,1)), start_at DATETIME NOT NULL, end_at DATETIME,
 created_by BIGINT NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(created_by) REFERENCES admin_account(id),
 CHECK(end_at IS NULL OR end_at > start_at)
);
CREATE INDEX idx_storefront_announcement_public ON storefront_announcement(enabled,pinned,start_at,end_at,sort_order,id);

CREATE TABLE inventory_document (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, document_no VARCHAR(60) NOT NULL UNIQUE,
 type VARCHAR(30) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
 supplier VARCHAR(100) NOT NULL DEFAULT '', reference_no VARCHAR(100) NOT NULL DEFAULT '', reason VARCHAR(200) NOT NULL DEFAULT '',
 version BIGINT NOT NULL DEFAULT 0, created_by BIGINT NOT NULL, posted_by BIGINT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, posted_at DATETIME NULL,
 FOREIGN KEY(created_by) REFERENCES admin_account(id), FOREIGN KEY(posted_by) REFERENCES admin_account(id),
 CHECK(type IN ('PURCHASE_IN','SUPPLIER_RETURN_OUT','STOCKTAKE')), CHECK(status IN ('DRAFT','POSTED')), CHECK(version >= 0)
);
CREATE INDEX idx_inventory_document_list ON inventory_document(type,status,created_at,id);
CREATE TABLE inventory_document_item (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, document_id BIGINT NOT NULL, sku_id BIGINT NOT NULL,
 product_name_snapshot VARCHAR(100) NOT NULL, specification_snapshot VARCHAR(100) NOT NULL,
 quantity INT NULL, counted_quantity INT NULL, before_on_hand INT NULL, before_reserved INT NULL,
 inventory_version BIGINT NULL, after_on_hand INT NULL, quantity_delta INT NULL,
 FOREIGN KEY(document_id) REFERENCES inventory_document(id), FOREIGN KEY(sku_id) REFERENCES product_sku(id),
 CONSTRAINT uk_inventory_document_sku UNIQUE(document_id,sku_id),
 CHECK(quantity IS NULL OR quantity > 0), CHECK(counted_quantity IS NULL OR counted_quantity >= 0),
 CHECK(before_on_hand IS NULL OR before_on_hand >= 0), CHECK(before_reserved IS NULL OR before_reserved >= 0),
 CHECK(after_on_hand IS NULL OR after_on_hand >= 0)
);
ALTER TABLE inventory_movement
 ADD COLUMN source_type VARCHAR(30) NULL;
ALTER TABLE inventory_movement ADD COLUMN source_id BIGINT NULL;
ALTER TABLE inventory_movement ADD COLUMN source_item_id BIGINT NULL;
ALTER TABLE inventory_movement ADD COLUMN source_no VARCHAR(100) NULL;
ALTER TABLE inventory_movement ADD COLUMN product_name_snapshot VARCHAR(100) NULL;
ALTER TABLE inventory_movement ADD COLUMN specification_snapshot VARCHAR(100) NULL;
CREATE INDEX idx_inventory_movement_source ON inventory_movement(source_type,source_id,source_item_id);
CREATE INDEX idx_inventory_movement_time ON inventory_movement(created_at,id);
