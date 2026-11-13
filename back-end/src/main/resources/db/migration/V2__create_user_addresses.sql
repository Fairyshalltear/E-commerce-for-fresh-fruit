CREATE TABLE user_address (
    id BIGINT NOT NULL AUTO_INCREMENT,
    buyer_id BIGINT NOT NULL,
    recipient_name VARCHAR(50) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    province VARCHAR(50) NOT NULL,
    city VARCHAR(50) NOT NULL,
    district VARCHAR(50) NOT NULL,
    detail_address VARCHAR(200) NOT NULL,
    address_tag VARCHAR(20) NOT NULL,
    is_default TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_address_buyer_created (buyer_id, created_at, id),
    CONSTRAINT chk_user_address_tag CHECK (address_tag IN ('HOME', 'COMPANY')),
    CONSTRAINT chk_user_address_default CHECK (is_default IN (0, 1)),
    CONSTRAINT fk_user_address_buyer FOREIGN KEY (buyer_id)
        REFERENCES buyer_account (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
