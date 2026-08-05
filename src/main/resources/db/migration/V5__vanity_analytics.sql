-- Vanity link analytics. Deliberately a separate pipeline from link analytics:
-- vanity links never expire, are addressed by username rather than short code, and their
-- history must survive the owner clearing or re-pointing their destination. Keying on
-- user_id (not a links row) is what gives us all three.

ALTER TABLE users ADD COLUMN vanity_click_count BIGINT NOT NULL DEFAULT 0;

CREATE TABLE vanity_events (
    id           BIGSERIAL   PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    clicked_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    referrer     TEXT,
    user_agent   TEXT,
    ip_hash      VARCHAR(64),
    country_code VARCHAR(2)
);

CREATE INDEX vanity_events_user_id_clicked_at_idx ON vanity_events (user_id, clicked_at DESC);
CREATE INDEX vanity_events_clicked_at_idx         ON vanity_events (clicked_at);

CREATE TABLE vanity_daily_clicks (
    user_id     UUID    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    day         DATE    NOT NULL,
    click_count BIGINT  NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, day)
);

CREATE INDEX vanity_daily_clicks_user_id_day_idx ON vanity_daily_clicks (user_id, day DESC);
