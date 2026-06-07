CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "citext";

CREATE TABLE users (
    id                                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    email                                   TEXT        NOT NULL UNIQUE,
    password_hash                           TEXT        NOT NULL,
    username                                CITEXT      NOT NULL UNIQUE,
    username_changed_at                     TIMESTAMPTZ,
    username_change_count_this_month        INTEGER     NOT NULL DEFAULT 0,
    vanity_destination                      TEXT,
    vanity_destination_change_count_this_month INTEGER  NOT NULL DEFAULT 0,
    vanity_destination_changed_at           TIMESTAMPTZ,
    created_at                              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX users_email_idx    ON users (email);
CREATE INDEX users_username_idx ON users (username);

CREATE TABLE links (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    code                CHAR(6)     NOT NULL UNIQUE,
    destination         TEXT        NOT NULL,
    owner_id            UUID        REFERENCES users (id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_clicked_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    inactivity_ttl_days INTEGER     NOT NULL DEFAULT 30,
    absolute_expires_at TIMESTAMPTZ,
    click_count         BIGINT      NOT NULL DEFAULT 0,
    is_expired          BOOLEAN     NOT NULL DEFAULT false,
    expired_at          TIMESTAMPTZ
);

CREATE INDEX links_code_idx             ON links (code);
CREATE INDEX links_owner_id_idx         ON links (owner_id);
CREATE INDEX links_last_clicked_at_idx  ON links (last_clicked_at) WHERE is_expired = false;
CREATE INDEX links_absolute_expires_at_idx ON links (absolute_expires_at)
    WHERE absolute_expires_at IS NOT NULL AND is_expired = false;

CREATE TABLE analytics_events (
    id           BIGSERIAL   PRIMARY KEY,
    link_id      UUID        NOT NULL REFERENCES links (id) ON DELETE CASCADE,
    clicked_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    referrer     TEXT,
    user_agent   TEXT,
    ip_hash      CHAR(64),
    country_code CHAR(2)
);

CREATE INDEX analytics_events_link_id_clicked_at_idx ON analytics_events (link_id, clicked_at DESC);
CREATE INDEX analytics_events_clicked_at_idx         ON analytics_events (clicked_at);

-- Auto-update updated_at on users
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
