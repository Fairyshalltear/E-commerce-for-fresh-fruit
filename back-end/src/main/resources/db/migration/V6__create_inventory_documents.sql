CREATE TABLE inventory_document (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, document_no VARCHAR(60) NOT NULL UNIQUE,
 type VARCHAR(30) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
 supplier VARCHAR(100) NOT NULL DEFAULT '', reference_no VARCHAR(100) NOT NULL DEFAULT '', reason VARCHAR(200) NOT NULL DEFAULT '',
 version BIGINT NOT NULL DEFAULT 0, created_by BIGINT NOT NULL, posted_by BIGINT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, posted_at DATETIME NULL,
 FOREIGN KEY(created_by) REFERENCES admin_account(id), FOREIGN KEY(posted_by) REFERENCES admin_account(id),
 CHECK(type IN ('PURCHASE_IN','SUPPLIER_RETURN_OUT','STOCKTAKE')), CHECK(status IN ('DRAFT','POSTED')), CHECK(version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX idx_inventory_document_list ON inventory_document(type,status,created_at,id);
CREATE TABLE inventory_document_item (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, document_id BIGINT NOT NULL, sku_id BIGINT NOT NULL,
 product_name_snapshot VARCHAR(100) NOT NULL, specification_snapshot VARCHAR(100) NOT NULL,
 quantity INT NULL, counted_quantity INT NULL, before_on_hand INT NULL, before_reserved INT NULL,
 inventory_version BIGINT NULL, after_on_hand INT NULL, quantity_delta INT NULL,
 FOREIGN KEY(document_id) REFERENCES inventory_document(id), FOREIGN KEY(sku_id) REFERENCES product_sku(id),
 UNIQUE KEY uk_inventory_document_sku(document_id,sku_id),
 CHECK(quantity IS NULL OR quantity > 0), CHECK(counted_quantity IS NULL OR counted_quantity >= 0),
 CHECK(before_on_hand IS NULL OR before_on_hand >= 0), CHECK(before_reserved IS NULL OR before_reserved >= 0),
 CHECK(after_on_hand IS NULL OR after_on_hand >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
ALTER TABLE inventory_movement
 ADD COLUMN source_type VARCHAR(30) NULL, ADD COLUMN source_id BIGINT NULL, ADD COLUMN source_item_id BIGINT NULL,
 ADD COLUMN source_no VARCHAR(100) NULL, ADD COLUMN product_name_snapshot VARCHAR(100) NULL,
 ADD COLUMN specification_snapshot VARCHAR(100) NULL;
CREATE INDEX idx_inventory_movement_source ON inventory_movement(source_type,source_id,source_item_id);
CREATE INDEX idx_inventory_movement_time ON inventory_movement(created_at,id);
-- Only uniquely verifiable business references are filled; historical name snapshots remain unknown.
UPDATE inventory_movement m SET
 source_type='ORDER',
 source_id=(SELECT MAX(o.id) FROM shop_order o JOIN order_item oi ON oi.order_id=o.id WHERE oi.sku_id=m.sku_id AND m.reason=CONCAT('订单发货 ',o.order_no) AND oi.shipped_quantity=-m.quantity_delta),
 source_item_id=(SELECT MAX(oi.id) FROM shop_order o JOIN order_item oi ON oi.order_id=o.id WHERE oi.sku_id=m.sku_id AND m.reason=CONCAT('订单发货 ',o.order_no) AND oi.shipped_quantity=-m.quantity_delta),
 source_no=(SELECT MAX(o.order_no) FROM shop_order o JOIN order_item oi ON oi.order_id=o.id WHERE oi.sku_id=m.sku_id AND m.reason=CONCAT('订单发货 ',o.order_no) AND oi.shipped_quantity=-m.quantity_delta)
 WHERE m.type='ORDER_DISPATCH' AND m.source_id IS NULL AND
 (SELECT COUNT(*) FROM shop_order o JOIN order_item oi ON oi.order_id=o.id WHERE oi.sku_id=m.sku_id AND m.reason=CONCAT('订单发货 ',o.order_no) AND oi.shipped_quantity=-m.quantity_delta)=1;
UPDATE inventory_movement m SET
 source_type='AFTER_SALE',
 source_id=(SELECT MAX(a.id) FROM after_sale_request a JOIN after_sale_item ai ON ai.request_id=a.id JOIN order_item oi ON oi.id=ai.order_item_id WHERE oi.sku_id=m.sku_id AND m.reason=CONCAT('退货入库 ',a.request_no) AND ai.restock_quantity=m.quantity_delta),
 source_item_id=(SELECT MAX(ai.id) FROM after_sale_request a JOIN after_sale_item ai ON ai.request_id=a.id JOIN order_item oi ON oi.id=ai.order_item_id WHERE oi.sku_id=m.sku_id AND m.reason=CONCAT('退货入库 ',a.request_no) AND ai.restock_quantity=m.quantity_delta),
 source_no=(SELECT MAX(a.request_no) FROM after_sale_request a JOIN after_sale_item ai ON ai.request_id=a.id JOIN order_item oi ON oi.id=ai.order_item_id WHERE oi.sku_id=m.sku_id AND m.reason=CONCAT('退货入库 ',a.request_no) AND ai.restock_quantity=m.quantity_delta)
 WHERE m.type='RETURN_RESTOCK' AND m.source_id IS NULL AND
 (SELECT COUNT(*) FROM after_sale_request a JOIN after_sale_item ai ON ai.request_id=a.id JOIN order_item oi ON oi.id=ai.order_item_id WHERE oi.sku_id=m.sku_id AND m.reason=CONCAT('退货入库 ',a.request_no) AND ai.restock_quantity=m.quantity_delta)=1;
