ALTER TABLE ai_model_config ADD COLUMN deleted_at DATETIME(6) NULL;
CREATE INDEX idx_ai_model_visible ON ai_model_config(deleted_at,id);
