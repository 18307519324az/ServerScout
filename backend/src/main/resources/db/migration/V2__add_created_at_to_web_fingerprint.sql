-- Ensure web_fingerprint.created_at exists for JPA entity mapping compatibility.
-- This migration is idempotent and safe on existing databases.

SET @has_col := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'web_fingerprint'
    AND COLUMN_NAME = 'created_at'
);

SET @ddl := IF(
  @has_col = 0,
  'ALTER TABLE web_fingerprint ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)',
  'SELECT ''web_fingerprint.created_at already exists'''
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
