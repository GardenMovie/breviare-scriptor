CREATE TABLE link_daily_clicks (
    link_id     UUID    NOT NULL REFERENCES links (id) ON DELETE CASCADE,
    day         DATE    NOT NULL,
    click_count BIGINT  NOT NULL DEFAULT 0,
    PRIMARY KEY (link_id, day)
);

CREATE INDEX link_daily_clicks_link_id_day_idx ON link_daily_clicks (link_id, day DESC);
