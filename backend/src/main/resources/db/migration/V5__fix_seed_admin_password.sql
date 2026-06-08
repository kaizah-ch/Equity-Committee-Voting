-- Align seeded admin credentials with V2 comment: admin@equity.com / Admin@1234
UPDATE users
SET password = '$2a$10$K1UMvhev1WgOni0/DEDUvu3jftN8BZh8KiaBHdhKIozs2ulySnmnm',
    is_active = TRUE
WHERE email = 'admin@equity.com';
