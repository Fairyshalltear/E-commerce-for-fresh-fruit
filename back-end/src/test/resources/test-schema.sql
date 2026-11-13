CREATE TABLE buyer_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    phone VARCHAR(20) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    nickname VARCHAR(50),
    avatar_url VARCHAR(500),
    status INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE admin_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(50),
    status INTEGER NOT NULL DEFAULT 1
);

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
 id BIGINT AUTO_INCREMENT PRIMARY KEY, admin_id BIGINT NOT NULL,
 operation_type VARCHAR(50) NOT NULL, target_type VARCHAR(30) NOT NULL, target_id BIGINT NOT NULL,
 result VARCHAR(20) NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(admin_id) REFERENCES admin_account(id)
);
CREATE TABLE inventory_movement (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, sku_id BIGINT NOT NULL, operation_log_id BIGINT NOT NULL,
 type VARCHAR(40) NOT NULL, before_on_hand INT NOT NULL, after_on_hand INT NOT NULL, quantity_delta INT NOT NULL,
 reason VARCHAR(200) NOT NULL, admin_id BIGINT NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(sku_id) REFERENCES product_sku(id), FOREIGN KEY(operation_log_id) REFERENCES admin_operation_log(id),
 FOREIGN KEY(admin_id) REFERENCES admin_account(id), CHECK(after_on_hand >= 0), CHECK(before_on_hand >= 0)
);
CREATE INDEX idx_movement_sku_time ON inventory_movement(sku_id,created_at,id);
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
