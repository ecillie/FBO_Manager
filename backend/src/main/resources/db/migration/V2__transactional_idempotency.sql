-- Idempotency records are claimed and completed in the same transaction as the
-- business effect. An uncommitted unique-key insert serializes concurrent retries;
-- rollback removes the claim so a later explicit retry can execute normally.
CREATE TABLE idempotency_records (
    api_version VARCHAR(16) NOT NULL,
    operation_id VARCHAR(160) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    command_fingerprint CHAR(64) NOT NULL,
    response_status SMALLINT NOT NULL,
    response_body JSONB NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ,
    fuel_transaction_id BIGINT,
    PRIMARY KEY (api_version, operation_id, idempotency_key),
    CONSTRAINT idempotency_records_fuel_transaction_fk
        FOREIGN KEY (fuel_transaction_id)
        REFERENCES fuel_inventory_transactions (fuel_transaction_id)
        ON DELETE RESTRICT,
    CONSTRAINT idempotency_records_api_version_ck
        CHECK (api_version ~ '^v[1-9][0-9]{0,4}$'),
    CONSTRAINT idempotency_records_operation_id_ck
        CHECK (operation_id = btrim(operation_id) AND operation_id <> ''),
    CONSTRAINT idempotency_records_key_ck
        CHECK (
            length(idempotency_key) BETWEEN 16 AND 128
            AND idempotency_key ~ '^[A-Za-z0-9._:-]+$'
        ),
    CONSTRAINT idempotency_records_fingerprint_ck
        CHECK (command_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT idempotency_records_response_status_ck
        CHECK (response_status BETWEEN 200 AND 599),
    CONSTRAINT idempotency_records_retention_ck
        CHECK (
            (
                fuel_transaction_id IS NULL
                AND expires_at >= completed_at + INTERVAL '24 hours'
            )
            OR (
                fuel_transaction_id IS NOT NULL
                AND expires_at IS NULL
            )
        )
);

CREATE INDEX idempotency_records_expiration_idx
    ON idempotency_records (expires_at)
    WHERE fuel_transaction_id IS NULL;

CREATE INDEX idempotency_records_fuel_transaction_idx
    ON idempotency_records (fuel_transaction_id)
    WHERE fuel_transaction_id IS NOT NULL;
