-- Local development users. Password for all users: Admin@1234
-- Password hashed with BCrypt (cost 10)
INSERT INTO users (id, email, password, full_name, role, is_active)
VALUES
    (
        gen_random_uuid(),
        'member@equity.com',
        '$2a$06$oOqG4y7OOzxjNZuTRWX5yex4Fg1VTYGBr1fUzM4bBF8tUiNCJWy6C',
        'Committee Member',
        'COMMITTEE_MEMBER',
        TRUE
    ),
    (
        gen_random_uuid(),
        'chair@equity.com',
        '$2a$06$oOqG4y7OOzxjNZuTRWX5yex4Fg1VTYGBr1fUzM4bBF8tUiNCJWy6C',
        'Committee Chairperson',
        'CHAIRPERSON',
        TRUE
    ),
    (
        gen_random_uuid(),
        'secretary@equity.com',
        '$2a$06$oOqG4y7OOzxjNZuTRWX5yex4Fg1VTYGBr1fUzM4bBF8tUiNCJWy6C',
        'Committee Secretary',
        'SECRETARY',
        TRUE
    )
ON CONFLICT (email) DO NOTHING;
