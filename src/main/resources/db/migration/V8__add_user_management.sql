CREATE TABLE users (
    id              BIGSERIAL       PRIMARY KEY,
    email           VARCHAR(255)    NOT NULL UNIQUE,
    password_hash   VARCHAR(255),
    password_salt   VARCHAR(255),
    display_name    VARCHAR(255)    NOT NULL,
    role            VARCHAR(50)     NOT NULL DEFAULT 'USER',
    status          VARCHAR(50)     NOT NULL DEFAULT 'PENDING',
    auth_provider   VARCHAR(50)     NOT NULL DEFAULT 'LOCAL',
    provider_id     VARCHAR(255),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE tenants (
    id              VARCHAR(255)    PRIMARY KEY,
    name            VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE user_tenants (
    user_id         BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tenant_id       VARCHAR(255)    NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, tenant_id)
);

CREATE INDEX idx_users_email      ON users (email);
CREATE INDEX idx_user_tenants_uid ON user_tenants (user_id);
CREATE INDEX idx_user_tenants_tid ON user_tenants (tenant_id);
