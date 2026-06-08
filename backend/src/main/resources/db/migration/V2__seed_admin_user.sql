-- Default admin user: admin@equity.com / Admin@1234
-- Password hashed with BCrypt (cost 10)
INSERT INTO users (id, email, password, full_name, role, is_active)
VALUES (
    gen_random_uuid(),
    'admin@equity.com',
    '$2a$10$K1UMvhev1WgOni0/DEDUvu3jftN8BZh8KiaBHdhKIozs2ulySnmnm',
    'System Administrator',
    'ADMIN',
    TRUE
) ON CONFLICT (email) DO NOTHING;
