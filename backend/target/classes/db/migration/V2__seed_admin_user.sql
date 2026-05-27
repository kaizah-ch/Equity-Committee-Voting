-- Default admin user: admin@equity.com / Admin@1234
-- Password hashed with BCrypt (cost 10)
INSERT INTO users (id, email, password, full_name, role, is_active)
VALUES (
    gen_random_uuid(),
    'admin@equity.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'System Administrator',
    'ADMIN',
    TRUE
) ON CONFLICT (email) DO NOTHING;
