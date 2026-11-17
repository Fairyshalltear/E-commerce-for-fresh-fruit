ALTER TABLE fruit_product
 ADD COLUMN sweetness_description VARCHAR(200) NULL,
 ADD COLUMN seasonal_months JSON NULL,
 ADD COLUMN is_gift_box TINYINT NOT NULL DEFAULT 0,
 ADD COLUMN use_cases JSON NULL,
 ADD CONSTRAINT chk_product_gift_box CHECK(is_gift_box IN (0,1));

ALTER TABLE admin_permission DROP CHECK chk_admin_permission_key;
ALTER TABLE admin_permission ADD CONSTRAINT chk_admin_permission_key CHECK(permission_key IN (
 'PRODUCTS','BUYERS','ORDERS','INVENTORY','AFTER_SALES','CONTENT','STATISTICS','AI',
 'REFUND_EXECUTE','PRODUCT_STATUS_CHANGE','INVENTORY_ADJUST'
));
