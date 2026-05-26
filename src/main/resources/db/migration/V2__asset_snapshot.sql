CREATE TABLE asset_snapshots (
    year_month   VARCHAR(7)  PRIMARY KEY,                        -- 'YYYY-MM'
    total_assets BIGINT      NOT NULL CHECK (total_assets >= 0),
    debt         BIGINT      NOT NULL DEFAULT 0 CHECK (debt >= 0),
    memo         VARCHAR(500),
    updated_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_ym_format CHECK (year_month LIKE '____-__')
);
