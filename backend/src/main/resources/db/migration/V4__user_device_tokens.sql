CREATE TABLE user_device_tokens (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token         VARCHAR(4096) NOT NULL UNIQUE,
    platform      VARCHAR(50),
    last_seen_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_device_tokens_user_id ON user_device_tokens(user_id);
