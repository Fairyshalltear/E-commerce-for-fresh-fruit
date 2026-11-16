ALTER TABLE admin_account
 ADD COLUMN account_type VARCHAR(20) NOT NULL DEFAULT 'OWNER',
 ADD COLUMN credential_version BIGINT NOT NULL DEFAULT 0,
 ADD CONSTRAINT chk_admin_account_type CHECK (account_type IN ('OWNER','STAFF'));

CREATE TABLE admin_permission (
 admin_id BIGINT NOT NULL,
 permission_scope VARCHAR(20) NOT NULL,
 permission_key VARCHAR(40) NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(admin_id,permission_scope,permission_key),
 CONSTRAINT fk_admin_permission_account FOREIGN KEY(admin_id) REFERENCES admin_account(id),
 CONSTRAINT chk_admin_permission_scope CHECK(permission_scope IN ('MODULE','OPERATION')),
 CONSTRAINT chk_admin_permission_key CHECK(permission_key IN (
  'PRODUCTS','BUYERS','ORDERS','INVENTORY','AFTER_SALES','CONTENT','STATISTICS',
  'REFUND_EXECUTE','PRODUCT_STATUS_CHANGE','INVENTORY_ADJUST'
 ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX idx_admin_permission_lookup ON admin_permission(permission_scope,permission_key,admin_id);

ALTER TABLE admin_operation_log
 MODIFY COLUMN admin_id BIGINT NULL,
 MODIFY COLUMN target_type VARCHAR(30) NULL,
 MODIFY COLUMN target_id BIGINT NULL,
 ADD COLUMN event_category VARCHAR(30) NULL,
 ADD COLUMN actor_username_snapshot VARCHAR(50) NULL,
 ADD COLUMN source_ip VARCHAR(45) NULL,
 ADD COLUMN request_path VARCHAR(255) NULL,
 ADD COLUMN target_summary VARCHAR(500) NULL,
 ADD COLUMN failure_category VARCHAR(80) NULL;
CREATE INDEX idx_admin_log_actor_time ON admin_operation_log(admin_id,created_at,id);
CREATE INDEX idx_admin_log_operation_time ON admin_operation_log(operation_type,created_at,id);
CREATE INDEX idx_admin_log_result_time ON admin_operation_log(result,created_at,id);

CREATE TABLE buyer_login_activity (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 buyer_id BIGINT NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_buyer_login_activity_buyer FOREIGN KEY(buyer_id) REFERENCES buyer_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX idx_buyer_login_activity_time_buyer ON buyer_login_activity(created_at,buyer_id);

CREATE TABLE storefront_carousel (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 image_id BIGINT NOT NULL,
 title VARCHAR(100) NULL,
 target_url VARCHAR(500) NULL,
 sort_order INT NOT NULL DEFAULT 0,
 enabled TINYINT NOT NULL DEFAULT 0,
 start_at DATETIME NOT NULL,
 end_at DATETIME NULL,
 created_by BIGINT NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_storefront_carousel_image FOREIGN KEY(image_id) REFERENCES product_image(id),
 CONSTRAINT fk_storefront_carousel_admin FOREIGN KEY(created_by) REFERENCES admin_account(id),
 CONSTRAINT chk_storefront_carousel_enabled CHECK(enabled IN (0,1)),
 CONSTRAINT chk_storefront_carousel_sort CHECK(sort_order >= 0),
 CONSTRAINT chk_storefront_carousel_window CHECK(end_at IS NULL OR end_at > start_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX idx_storefront_carousel_public ON storefront_carousel(enabled,start_at,end_at,sort_order,id);

CREATE TABLE storefront_announcement (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 title VARCHAR(150) NOT NULL,
 summary VARCHAR(500) NOT NULL DEFAULT '',
 body TEXT NOT NULL,
 sort_order INT NOT NULL DEFAULT 0,
 pinned TINYINT NOT NULL DEFAULT 0,
 enabled TINYINT NOT NULL DEFAULT 0,
 start_at DATETIME NOT NULL,
 end_at DATETIME NULL,
 created_by BIGINT NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_storefront_announcement_admin FOREIGN KEY(created_by) REFERENCES admin_account(id),
 CONSTRAINT chk_storefront_announcement_pinned CHECK(pinned IN (0,1)),
 CONSTRAINT chk_storefront_announcement_enabled CHECK(enabled IN (0,1)),
 CONSTRAINT chk_storefront_announcement_sort CHECK(sort_order >= 0),
 CONSTRAINT chk_storefront_announcement_window CHECK(end_at IS NULL OR end_at > start_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX idx_storefront_announcement_public ON storefront_announcement(enabled,pinned,start_at,end_at,sort_order,id);

CREATE INDEX idx_buyer_created_at ON buyer_account(created_at,id);
CREATE INDEX idx_order_created_buyer_status ON shop_order(created_at,buyer_id,status,id);
CREATE INDEX idx_payment_result_time ON payment_record(result,created_at,order_id,id);
CREATE INDEX idx_refund_result_time ON refund_record(result,created_at,order_id,id);
