-- Two accounts for the smoke test user (public_id = 11111111-1111-1111-1111-111111111111).
-- Both have 1000.00 USD. Different `name` because of the (user_id, currency, name) unique constraint.
INSERT INTO accounts (public_id, user_id, balance, currency, name, version, created_at, updated_at)
VALUES
    ('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 1000.00, 'USD', 'Checking', 0, now(), now()),
    ('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 1000.00, 'USD', 'Savings',  0, now(), now())
ON CONFLICT (public_id) DO NOTHING;