-- ============================================
-- indexes.sql
-- Indexes for query optimizations
-- ============================================

-- ============================================
-- 1) Indexes for feature: "Last 10 user's transfers"
-- request: GET /api/transfers/ten-recent/{userId}
-- description:
--      The idea is to find 10 latest transfers for a specific user where he's either receiver and sender.
-- ============================================

-- Index for transfers search where user is sender
-- Covers: from_account_id, created_at DESC

CREATE INDEX IF NOT EXISTS idx_transfers_from_account_created
    ON transfers(from_account_id, created_at DESC);

-- Index for transfers search where user is receiver
-- Covers: to_account_id, created_at DESC

CREATE INDEX IF NOT EXISTS idx_transfers_to_account_created
    ON transfers(to_account_id, created_at DESC);

-- ============================================
-- 2) Indexes for feature: "Sender Leaderboard For Amount of Days"
-- request: GET /api/admin/transfers/leaderboard
-- description:
--      The idea is to find top N sender-accounts for last M days (you provide those values as request params).
--      For enormous amounts of transfers we can make a partitioning and create an index for partition tables,
--      you can see necessary SQL in 'classpath:/test/java/resources/sql/features/top_transfers_last_days'
-- ============================================

-- Index for 'transfers' table (if we don't use partitioning),
--  searching transfers with status 'COMPLETED' (it's a part of a logic to find among 'COMPLETED'-only)
-- Covers: from_account_id, created_at DESC
-- Additional constraint: WHERE status = 'COMPLETED'
CREATE INDEX idx_transfers_from_account_id_created_completed
    ON transfers(from_account_id, created_at DESC)
	WHERE status = 'COMPLETED'