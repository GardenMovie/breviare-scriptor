ALTER TABLE users
    ALTER COLUMN username DROP NOT NULL,
    ADD COLUMN provider         TEXT NOT NULL DEFAULT 'google',
    ADD COLUMN provider_user_id TEXT NOT NULL DEFAULT '';

ALTER TABLE users
    ALTER COLUMN provider_user_id DROP DEFAULT,
    ADD CONSTRAINT users_provider_identity_unique UNIQUE (provider, provider_user_id);
