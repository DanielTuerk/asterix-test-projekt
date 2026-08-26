CREATE TABLE IF NOT EXISTS flight_plan
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    ifpl_id
    VARCHAR
(
    100
) NOT NULL,
    title VARCHAR
(
    255
),
    arcid VARCHAR
(
    50
),
    adep VARCHAR
(
    10
),
    ades VARCHAR
(
    10
),
    arctyp VARCHAR
(
    50
),
    eobd VARCHAR
(
    20
),
    eobt VARCHAR
(
    20
),
    rfl VARCHAR
(
    20
),
    speed VARCHAR
(
    20
),
    route TEXT,
    origin_fac VARCHAR
(
    100
),

    addresses JSONB,
    route_points JSONB,
    eet_per_fir JSONB,

    raw_payload BYTEA,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_flight_plan_ifpl_id UNIQUE
(
    ifpl_id
)
    );

CREATE INDEX IF NOT EXISTS idx_flight_plan_arcid
    ON flight_plan (arcid);

CREATE INDEX IF NOT EXISTS idx_flight_plan_adep_ades
    ON flight_plan (adep, ades);

CREATE INDEX IF NOT EXISTS idx_flight_plan_received_at
    ON flight_plan (received_at);