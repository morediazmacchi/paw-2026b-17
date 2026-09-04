CREATE TABLE IF NOT EXISTS category (
    id      SERIAL PRIMARY KEY,
    name    VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS users (
    id      SERIAL PRIMARY KEY,
    email   VARCHAR(150) NOT NULL UNIQUE,
    name    VARCHAR(150) NOT NULL
);

-- image bytes live in Postgres directly (BYTEA), never Base64-encoded
CREATE TABLE IF NOT EXISTS image (
    id             SERIAL PRIMARY KEY,
    data           BYTEA NOT NULL,
    content_type   VARCHAR(100) NOT NULL,
    file_name      VARCHAR(255),
    file_size      INTEGER,
    checksum       VARCHAR(64),
    uploaded_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS event (
    id              SERIAL PRIMARY KEY,
    title           VARCHAR(150) NOT NULL,
    description     TEXT,
    event_date      TIMESTAMP NOT NULL,
    address         VARCHAR(200) NOT NULL,
    neighborhood    VARCHAR(80),
    image_id        INTEGER, -- soft reference to image.id, intentionally not a FK
    price           NUMERIC(10,2) NOT NULL DEFAULT 0
                        CHECK (price >= 0),
    max_capacity    INTEGER
                        CHECK (max_capacity IS NULL OR max_capacity > 0),
    creator_email   VARCHAR(150) NOT NULL,
    category_id     INTEGER NOT NULL REFERENCES category(id),
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS registration (
    id                SERIAL PRIMARY KEY,
    event_id          INTEGER NOT NULL REFERENCES event(id) ON DELETE CASCADE,
    email             VARCHAR(150) NOT NULL,
    name              VARCHAR(150),
    registered_at     TIMESTAMP NOT NULL DEFAULT now(),
    status            VARCHAR(20) NOT NULL DEFAULT 'confirmed'
                          CHECK (status IN ('confirmed', 'waitlisted')),
    UNIQUE (event_id, email)
);
