CREATE TABLE ai_model_config (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(100) NOT NULL,
 chat_base_url VARCHAR(500) NOT NULL DEFAULT '', chat_model VARCHAR(100) NOT NULL,
 chat_key_cipher TEXT NULL,
 embedding_base_url VARCHAR(500) NOT NULL DEFAULT '', embedding_model VARCHAR(100) NOT NULL,
 embedding_key_cipher TEXT NULL, embedding_dimensions INT NOT NULL DEFAULT 1024,
 prompt_template TEXT NOT NULL, temperature DECIMAL(3,2) NOT NULL DEFAULT 0.20,
 max_output_tokens INT NOT NULL DEFAULT 1200, history_turns INT NOT NULL DEFAULT 10,
 connect_timeout_seconds INT NOT NULL DEFAULT 10, first_content_seconds INT NOT NULL DEFAULT 20,
 idle_timeout_seconds INT NOT NULL DEFAULT 15, total_timeout_seconds INT NOT NULL DEFAULT 90,
 buyer_per_minute INT NOT NULL DEFAULT 10, guest_per_minute INT NOT NULL DEFAULT 3,
 buyer_daily INT NOT NULL DEFAULT 100, guest_daily INT NOT NULL DEFAULT 20,
 global_daily_calls INT NOT NULL DEFAULT 1000,
 enabled TINYINT NOT NULL DEFAULT 0, is_active TINYINT NOT NULL DEFAULT 0,
 created_by BIGINT NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(created_by) REFERENCES admin_account(id),
 CHECK(enabled IN (0,1) AND is_active IN (0,1)),
 CHECK(embedding_dimensions BETWEEN 64 AND 2048),
 CHECK(temperature BETWEEN 0 AND 2 AND max_output_tokens BETWEEN 64 AND 4096),
 CHECK(history_turns BETWEEN 0 AND 10)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_embedding_profile (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, config_id BIGINT NOT NULL,
 model VARCHAR(100) NOT NULL, dimensions INT NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'BUILDING',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), activated_at DATETIME(6) NULL,
 FOREIGN KEY(config_id) REFERENCES ai_model_config(id),
 CHECK(status IN ('BUILDING','READY','ACTIVE','FAILED','RETIRED')),
 CHECK(dimensions BETWEEN 64 AND 2048)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_knowledge_document (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, type VARCHAR(20) NOT NULL,
 origin_kind VARCHAR(20) NOT NULL DEFAULT 'MANUAL', catalog_key VARCHAR(80) NULL UNIQUE,
 product_id BIGINT NULL, title VARCHAR(150) NOT NULL, draft_body TEXT NOT NULL,
 draft_question VARCHAR(300) NULL, draft_aliases JSON NULL,
 source_label VARCHAR(150) NOT NULL DEFAULT '', enabled TINYINT NOT NULL DEFAULT 0,
 active_revision_id BIGINT NULL, latest_revision_id BIGINT NULL,
 version BIGINT NOT NULL DEFAULT 0, index_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
 index_error VARCHAR(300) NULL, created_by BIGINT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(product_id) REFERENCES fruit_product(id), FOREIGN KEY(created_by) REFERENCES admin_account(id),
 CHECK(type IN ('FRUIT','PRODUCT','FAQ','POLICY')),
 CHECK(origin_kind IN ('MANUAL','CATALOG')), CHECK(enabled IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX idx_ai_knowledge_state ON ai_knowledge_document(enabled,index_status,id);
CREATE INDEX idx_ai_knowledge_product ON ai_knowledge_document(product_id,origin_kind);

CREATE TABLE ai_knowledge_revision (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, document_id BIGINT NOT NULL, revision_no BIGINT NOT NULL,
 title VARCHAR(150) NOT NULL, body TEXT NOT NULL, standard_question VARCHAR(300) NULL, aliases JSON NULL,
 source_label VARCHAR(150) NOT NULL, product_version BIGINT NULL,
 created_by BIGINT NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE(document_id,revision_no), FOREIGN KEY(document_id) REFERENCES ai_knowledge_document(id),
 FOREIGN KEY(created_by) REFERENCES admin_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
ALTER TABLE ai_knowledge_document ADD CONSTRAINT fk_ai_document_active FOREIGN KEY(active_revision_id) REFERENCES ai_knowledge_revision(id);
ALTER TABLE ai_knowledge_document ADD CONSTRAINT fk_ai_document_latest FOREIGN KEY(latest_revision_id) REFERENCES ai_knowledge_revision(id);

CREATE TABLE ai_knowledge_chunk (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, revision_id BIGINT NOT NULL,
 ordinal INT NOT NULL, content TEXT NOT NULL,
 UNIQUE(revision_id,ordinal), FOREIGN KEY(revision_id) REFERENCES ai_knowledge_revision(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_chunk_embedding (
 chunk_id BIGINT NOT NULL, profile_id BIGINT NOT NULL, vector_json JSON NOT NULL,
 PRIMARY KEY(chunk_id,profile_id), FOREIGN KEY(chunk_id) REFERENCES ai_knowledge_chunk(id),
 FOREIGN KEY(profile_id) REFERENCES ai_embedding_profile(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_index_task (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, document_id BIGINT NULL, revision_id BIGINT NOT NULL,
 profile_id BIGINT NULL, kind VARCHAR(20) NOT NULL DEFAULT 'PUBLISH',
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING', expected_version BIGINT NOT NULL,
 attempts INT NOT NULL DEFAULT 0, lease_until DATETIME(6) NULL, last_error VARCHAR(300) NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(document_id) REFERENCES ai_knowledge_document(id),
 FOREIGN KEY(revision_id) REFERENCES ai_knowledge_revision(id), FOREIGN KEY(profile_id) REFERENCES ai_embedding_profile(id),
 CHECK(status IN ('PENDING','RUNNING','DONE','FAILED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX idx_ai_index_queue ON ai_index_task(status,lease_until,id);

CREATE TABLE ai_conversation (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, owner_kind VARCHAR(10) NOT NULL,
 buyer_id BIGINT NULL, guest_token_hash VARCHAR(64) NULL,
 title VARCHAR(100) NOT NULL DEFAULT '新对话', context_json JSON NULL,
 active_request_id BIGINT NULL, epoch BIGINT NOT NULL DEFAULT 0, next_sequence BIGINT NOT NULL DEFAULT 0,
 expires_at DATETIME(6) NOT NULL, deleted_at DATETIME(6) NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(buyer_id) REFERENCES buyer_account(id), CHECK(owner_kind IN ('BUYER','GUEST')),
 CHECK((owner_kind='BUYER' AND buyer_id IS NOT NULL AND guest_token_hash IS NULL) OR
       (owner_kind='GUEST' AND buyer_id IS NULL AND guest_token_hash IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX idx_ai_conversation_buyer ON ai_conversation(buyer_id,updated_at,id);
CREATE INDEX idx_ai_conversation_guest ON ai_conversation(guest_token_hash,expires_at);
CREATE INDEX idx_ai_conversation_retention ON ai_conversation(expires_at,deleted_at,id);

CREATE TABLE ai_message (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, conversation_id BIGINT NOT NULL, sequence_no BIGINT NOT NULL,
 role VARCHAR(12) NOT NULL, content TEXT NOT NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE(conversation_id,sequence_no), FOREIGN KEY(conversation_id) REFERENCES ai_conversation(id),
 CHECK(role IN ('USER','ASSISTANT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_request (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, conversation_id BIGINT NOT NULL, question_id BIGINT NOT NULL,
 answer_id BIGINT NOT NULL, client_request_key VARCHAR(80) NOT NULL, payload_hash VARCHAR(64) NOT NULL,
 retry_of BIGINT NULL, status VARCHAR(20) NOT NULL DEFAULT 'ACCEPTED',
 generation_epoch BIGINT NOT NULL, input_context JSON NULL, config_id BIGINT NULL, rules_snapshot JSON NULL,
 scene VARCHAR(30) NOT NULL DEFAULT 'GENERAL', answer_mode VARCHAR(20) NULL,
 retrieval_mode VARCHAR(20) NULL, cards_json JSON NULL, error_code VARCHAR(80) NULL, error_message VARCHAR(300) NULL,
 lease_until DATETIME(6) NOT NULL, first_content_at DATETIME(6) NULL, finished_at DATETIME(6) NULL,
 prompt_tokens BIGINT NULL, completion_tokens BIGINT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE(conversation_id,client_request_key), UNIQUE(answer_id),
 FOREIGN KEY(conversation_id) REFERENCES ai_conversation(id), FOREIGN KEY(question_id) REFERENCES ai_message(id),
 FOREIGN KEY(answer_id) REFERENCES ai_message(id), FOREIGN KEY(retry_of) REFERENCES ai_request(id),
 FOREIGN KEY(config_id) REFERENCES ai_model_config(id),
 CHECK(status IN ('ACCEPTED','RUNNING','COMPLETED','DEGRADED','FAILED','CANCELLED','BLOCKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX idx_ai_request_lease ON ai_request(status,lease_until,id);

CREATE TABLE ai_answer_source (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, answer_id BIGINT NOT NULL, ordinal INT NOT NULL,
 source_type VARCHAR(20) NOT NULL, source_id BIGINT NULL, revision_no BIGINT NULL,
 title VARCHAR(150) NOT NULL, excerpt VARCHAR(1000) NOT NULL, target_path VARCHAR(300) NULL,
 UNIQUE(answer_id,ordinal), FOREIGN KEY(answer_id) REFERENCES ai_message(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_feedback (
 answer_id BIGINT PRIMARY KEY, helpful TINYINT NOT NULL, reason VARCHAR(500) NULL,
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(answer_id) REFERENCES ai_message(id), CHECK(helpful IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_sensitive_rule (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL,
 scope VARCHAR(10) NOT NULL, match_mode VARCHAR(20) NOT NULL, terms_json JSON NOT NULL,
 priority INT NOT NULL DEFAULT 0, message VARCHAR(200) NOT NULL,
 enabled TINYINT NOT NULL DEFAULT 1, version BIGINT NOT NULL DEFAULT 0,
 created_by BIGINT NOT NULL, updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(created_by) REFERENCES admin_account(id),
 CHECK(scope IN ('INPUT','OUTPUT')), CHECK(match_mode IN ('LITERAL','ALL','ANY')),
 CHECK(enabled IN (0,1)), CHECK(scope='INPUT' OR match_mode='LITERAL')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_rule_hit (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, request_id BIGINT NULL, rule_id BIGINT NULL,
 rule_version BIGINT NOT NULL, stage VARCHAR(10) NOT NULL, summary VARCHAR(300) NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(rule_id) REFERENCES ai_sensitive_rule(id), CHECK(stage IN ('INPUT','OUTPUT','BUSINESS'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_question_fact (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, question_id BIGINT NULL UNIQUE,
 conversation_id BIGINT NULL, owner_kind VARCHAR(10) NOT NULL, buyer_id BIGINT NULL,
 scene VARCHAR(30) NOT NULL DEFAULT 'GENERAL', topic VARCHAR(150) NOT NULL DEFAULT '其他问题',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX idx_ai_question_stats ON ai_question_fact(created_at,scene,id);

CREATE TABLE ai_attempt_fact (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, request_id BIGINT NULL UNIQUE, conversation_id BIGINT NULL,
 status VARCHAR(20) NOT NULL, is_retry TINYINT NOT NULL DEFAULT 0,
 mode VARCHAR(20) NULL, scene VARCHAR(30) NOT NULL DEFAULT 'GENERAL', helpful TINYINT NULL,
 first_content_ms BIGINT NULL, total_ms BIGINT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CHECK(helpful IS NULL OR helpful IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX idx_ai_attempt_stats ON ai_attempt_fact(created_at,status,id);

CREATE TABLE ai_usage_counter (
 scope_key VARCHAR(120) NOT NULL, usage_date DATE NOT NULL, count_value INT NOT NULL DEFAULT 0,
 PRIMARY KEY(scope_key,usage_date), CHECK(count_value>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
