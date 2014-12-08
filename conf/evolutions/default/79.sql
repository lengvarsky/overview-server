# --- !Ups

BEGIN;

ALTER TABLE page ADD COLUMN data_size BIGINT;
UPDATE page SET data_size = LENGTH(data);
ALTER TABLE page ALTER COLUMN data_size SET NOT NULL;

COMMIT;

# --- !Downs

BEGIN;

ALTER TABLE page DROP COLUMN data_size;

COMMIT;


