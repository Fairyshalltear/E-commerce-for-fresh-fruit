CREATE TABLE shop_order (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 order_no VARCHAR(40) NOT NULL UNIQUE,
 buyer_id BIGINT NOT NULL,
 idempotency_key VARCHAR(80) NOT NULL,
 status VARCHAR(20) NOT NULL,
 subtotal DECIMAL(10,2) NOT NULL,
 shipping_fee DECIMAL(10,2) NOT NULL DEFAULT 0,
 total_amount DECIMAL(10,2) NOT NULL,
 refunded_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
 recipient_name VARCHAR(50) NOT NULL,
 phone VARCHAR(20) NOT NULL,
 province VARCHAR(50) NOT NULL,
 city VARCHAR(50) NOT NULL,
 district VARCHAR(50) NOT NULL,
 detail_address VARCHAR(200) NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 paid_at DATETIME NULL,
 shipped_at DATETIME NULL,
 completed_at DATETIME NULL,
 cancelled_at DATETIME NULL,
 UNIQUE KEY uk_order_buyer_request (buyer_id,idempotency_key),
 KEY idx_order_buyer_status_time (buyer_id,status,created_at,id),
 KEY idx_order_status_time (status,created_at,id),
 FOREIGN KEY (buyer_id) REFERENCES buyer_account(id),
 CHECK (status IN ('PENDING_PAYMENT','PENDING_SHIPMENT','PENDING_RECEIPT','COMPLETED','CANCELLED')),
 CHECK (subtotal >= 0 AND shipping_fee >= 0 AND total_amount >= 0 AND refunded_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE order_item (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 order_id BIGINT NOT NULL,
 product_id BIGINT NOT NULL,
 sku_id BIGINT NOT NULL,
 product_name_snapshot VARCHAR(100) NOT NULL,
 specification_snapshot VARCHAR(100) NOT NULL,
 image_snapshot VARCHAR(300) NULL,
 unit_price DECIMAL(10,2) NOT NULL,
 quantity INT NOT NULL,
 shipped_quantity INT NOT NULL DEFAULT 0,
 refund_pending_quantity INT NOT NULL DEFAULT 0,
 refunded_quantity INT NOT NULL DEFAULT 0,
 restocked_quantity INT NOT NULL DEFAULT 0,
 line_amount DECIMAL(10,2) NOT NULL,
 KEY idx_order_item_order (order_id,id),
 KEY idx_order_item_sku (sku_id,order_id),
 FOREIGN KEY (order_id) REFERENCES shop_order(id),
 FOREIGN KEY (product_id) REFERENCES fruit_product(id),
 FOREIGN KEY (sku_id) REFERENCES product_sku(id),
 CHECK (quantity > 0 AND shipped_quantity >= 0 AND refund_pending_quantity >= 0 AND refunded_quantity >= 0 AND restocked_quantity >= 0),
 CHECK (unit_price > 0 AND line_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payment_record (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 order_id BIGINT NOT NULL,
 idempotency_key VARCHAR(80) NOT NULL,
 channel VARCHAR(20) NOT NULL,
 result VARCHAR(20) NOT NULL,
 amount DECIMAL(10,2) NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_payment_order_request (order_id,idempotency_key),
 KEY idx_payment_order_time (order_id,created_at,id),
 FOREIGN KEY (order_id) REFERENCES shop_order(id),
 CHECK (channel IN ('ALIPAY','WECHAT')),
 CHECK (result IN ('SUCCESS','FAILED','CANCELLED')),
 CHECK (amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE shipment (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 order_id BIGINT NOT NULL UNIQUE,
 carrier VARCHAR(80) NOT NULL,
 tracking_number VARCHAR(100) NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (order_id) REFERENCES shop_order(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE order_status_event (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 order_id BIGINT NOT NULL,
 from_status VARCHAR(20) NULL,
 to_status VARCHAR(20) NOT NULL,
 event_type VARCHAR(40) NOT NULL,
 actor_type VARCHAR(20) NOT NULL,
 actor_id BIGINT NULL,
 note VARCHAR(500) NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_order_event_time (order_id,created_at,id),
 FOREIGN KEY (order_id) REFERENCES shop_order(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE shipment_event (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 shipment_id BIGINT NOT NULL,
 description VARCHAR(500) NOT NULL,
 occurred_at DATETIME NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_shipment_event_time (shipment_id,occurred_at,id),
 FOREIGN KEY (shipment_id) REFERENCES shipment(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE after_sale_request (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 request_no VARCHAR(40) NOT NULL UNIQUE,
 order_id BIGINT NOT NULL,
 buyer_id BIGINT NOT NULL,
 type VARCHAR(20) NOT NULL,
 status VARCHAR(30) NOT NULL,
 reason VARCHAR(100) NOT NULL,
 description VARCHAR(1000) NOT NULL DEFAULT '',
 requested_amount DECIMAL(10,2) NOT NULL,
 review_reason VARCHAR(500) NULL,
 reviewed_by BIGINT NULL,
 reviewed_at DATETIME NULL,
 return_carrier VARCHAR(80) NULL,
 return_tracking_number VARCHAR(100) NULL,
 received_at DATETIME NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_after_sale_status_time (status,created_at,id),
 KEY idx_after_sale_order (order_id,buyer_id,created_at),
 FOREIGN KEY (order_id) REFERENCES shop_order(id),
 FOREIGN KEY (buyer_id) REFERENCES buyer_account(id),
 FOREIGN KEY (reviewed_by) REFERENCES admin_account(id),
 CHECK (type IN ('REFUND','RETURN')),
 CHECK (status IN ('PENDING_REVIEW','WAITING_RETURN','WAITING_RECEIPT','COMPLETED','REJECTED')),
 CHECK (requested_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE after_sale_item (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 request_id BIGINT NOT NULL,
 order_item_id BIGINT NOT NULL,
 quantity INT NOT NULL,
 refund_quantity INT NOT NULL DEFAULT 0,
 restock_quantity INT NOT NULL DEFAULT 0,
 unit_price DECIMAL(10,2) NOT NULL,
 UNIQUE KEY uk_after_sale_request_item (request_id,order_item_id),
 KEY idx_after_sale_item_order (order_item_id,request_id),
 FOREIGN KEY (request_id) REFERENCES after_sale_request(id),
 FOREIGN KEY (order_item_id) REFERENCES order_item(id),
 CHECK (quantity > 0 AND refund_quantity >= 0 AND restock_quantity >= 0 AND refund_quantity <= quantity AND restock_quantity <= quantity),
 CHECK (unit_price > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE refund_record (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 request_id BIGINT NOT NULL UNIQUE,
 order_id BIGINT NOT NULL,
 amount DECIMAL(10,2) NOT NULL,
 result VARCHAR(20) NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_refund_order_time (order_id,created_at,id),
 FOREIGN KEY (request_id) REFERENCES after_sale_request(id),
 FOREIGN KEY (order_id) REFERENCES shop_order(id),
 CHECK (amount >= 0),
 CHECK (result = 'SIMULATED_SUCCESS')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE product_review
 ADD COLUMN deleted_at DATETIME NULL,
 ADD COLUMN deleted_by BIGINT NULL,
 ADD COLUMN deletion_reason VARCHAR(500) NULL,
 ADD CONSTRAINT fk_review_deleted_by FOREIGN KEY (deleted_by) REFERENCES admin_account(id);

CREATE TABLE product_review_deletion_audit (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 review_id BIGINT NOT NULL,
 admin_id BIGINT NOT NULL,
 reason VARCHAR(500) NOT NULL,
 review_snapshot TEXT NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_review_delete_audit (review_id,created_at,id),
 FOREIGN KEY (review_id) REFERENCES product_review(id),
 FOREIGN KEY (admin_id) REFERENCES admin_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
