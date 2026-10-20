INSERT INTO users (public_id, external_id, email, username, password, role, created_at, updated_at)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'smoke-user',
    'smoke@test.com',
    'smoke',
    '{bcrypt}$2a$10$qENC6aUj4EhPkbSHbx7Bc.l/gTTVfERc7gAUk.Md6Umi60T5qAgty',
    'USER',
    now(),
    now()
)
ON CONFLICT (email) DO NOTHING;