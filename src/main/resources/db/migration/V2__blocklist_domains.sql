CREATE TABLE blocklist_domains (
    domain      VARCHAR(253) PRIMARY KEY,
    source      VARCHAR(64)  NOT NULL,
    synced_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
