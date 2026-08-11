
CREATE TABLE transfers_partitioned (
    id BIGSERIAL,
    public_id UUID NOT NULL,
    from_account_id BIGINT NOT NULL,
    to_account_id BIGINT NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    reason TEXT,
    created_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

ALTER TABLE transfers_partitioned
    ADD CONSTRAINT fk_transfers_from_account
    FOREIGN KEY (from_account_id) REFERENCES accounts(id);

ALTER TABLE transfers_partitioned
    ADD CONSTRAINT fk_transfers_to_account
    FOREIGN KEY (to_account_id) REFERENCES accounts(id);

CREATE INDEX idx_transfers_from_account_id_created_completed
    ON transfers_partitioned(from_account_id, created_at DESC)
    WHERE status = 'COMPLETED'

-- CREATE TABLE transfers_date1_date2 PARTITION OF transfers_partitioned
--    FOR VALUES FROM ('date1') TO ('date2'); -- place the dates here

INSERT INTO transfers_partitioned
SELECT
    id,
    public_id,
    from_account_id,
    to_account_id,
    amount,
    status,
    reason,
    created_at,
    processed_at
FROM transfers;

-- check index stats:
--SELECT
--    relname,
--    indexrelname,
--    idx_scan,
--    idx_tup_read,
--    idx_tup_fetch,
--    pg_size_pretty(pg_relation_size(indexrelname::regclass)) as index_size
--FROM pg_stat_user_indexes
--WHERE relname LIKE 'transfers%'
--ORDER BY idx_scan DESC;