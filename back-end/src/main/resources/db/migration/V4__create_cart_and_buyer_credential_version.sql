ALTER TABLE buyer_account
    ADD COLUMN credential_version BIGINT NOT NULL DEFAULT 0 AFTER status;

CREATE TABLE cart_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    buyer_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    selected TINYINT UNSIGNED NOT NULL DEFAULT 1,
    accepted_unit_price DECIMAL(10,2) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_item_buyer_sku (buyer_id, sku_id),
    KEY idx_cart_item_buyer_created (buyer_id, created_at, id),
    CONSTRAINT chk_cart_item_quantity CHECK (quantity > 0),
    CONSTRAINT chk_cart_item_selected CHECK (selected IN (0, 1)),
    CONSTRAINT chk_cart_item_price CHECK (accepted_unit_price > 0),
    CONSTRAINT fk_cart_item_buyer FOREIGN KEY (buyer_id)
        REFERENCES buyer_account (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_item_sku FOREIGN KEY (sku_id)
        REFERENCES product_sku (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
