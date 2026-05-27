-- Users
CREATE TABLE users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email       VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    full_name   VARCHAR(255) NOT NULL,
    role        VARCHAR(50)  NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Cases
CREATE TABLE cases (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_number    VARCHAR(100) NOT NULL UNIQUE,
    client_name         VARCHAR(255) NOT NULL,
    requested_amount    NUMERIC(18,2) NOT NULL,
    product_type        VARCHAR(100) NOT NULL,
    tenure              VARCHAR(100),
    summary             TEXT,
    risk_notes          TEXT,
    collateral_summary  TEXT,
    status              VARCHAR(50)  NOT NULL DEFAULT 'DRAFT',
    voting_deadline     TIMESTAMP,
    verdict             VARCHAR(50),
    created_by          UUID NOT NULL REFERENCES users(id),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Case Messages
CREATE TABLE case_messages (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id           UUID NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    sender_id         UUID NOT NULL REFERENCES users(id),
    message_text      TEXT NOT NULL,
    parent_message_id UUID REFERENCES case_messages(id),
    created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Case Images
CREATE TABLE case_images (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id      UUID NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    uploaded_by  UUID NOT NULL REFERENCES users(id),
    image_url    VARCHAR(2048) NOT NULL,
    caption      VARCHAR(500),
    sort_order   INTEGER NOT NULL DEFAULT 0,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Votes
CREATE TABLE votes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id     UUID NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    voter_id    UUID NOT NULL REFERENCES users(id),
    vote_choice VARCHAR(50) NOT NULL,
    reason      TEXT,
    voted_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_vote_per_user_per_case UNIQUE (case_id, voter_id)
);

-- Notifications
CREATE TABLE notifications (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type       VARCHAR(100) NOT NULL,
    title      VARCHAR(255) NOT NULL,
    body       TEXT NOT NULL,
    case_id    UUID REFERENCES cases(id) ON DELETE SET NULL,
    is_read    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Audit Logs
CREATE TABLE audit_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(100) NOT NULL,
    entity_id   UUID NOT NULL,
    action      VARCHAR(100) NOT NULL,
    actor_id    UUID NOT NULL REFERENCES users(id),
    metadata    JSONB,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Refresh Tokens
CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token      VARCHAR(512) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_cases_status        ON cases(status);
CREATE INDEX idx_cases_created_by    ON cases(created_by);
CREATE INDEX idx_messages_case_id    ON case_messages(case_id);
CREATE INDEX idx_images_case_id      ON case_images(case_id);
CREATE INDEX idx_votes_case_id       ON votes(case_id);
CREATE INDEX idx_notifications_user  ON notifications(user_id, is_read);
CREATE INDEX idx_audit_entity        ON audit_logs(entity_type, entity_id);
