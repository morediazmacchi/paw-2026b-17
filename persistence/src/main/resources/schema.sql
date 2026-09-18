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
    price           NUMERIC(10,2) DEFAULT 0 NOT NULL
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
    status            VARCHAR(20) DEFAULT 'confirmed' NOT NULL
                          CHECK (status IN ('confirmed', 'waitlisted')),
    UNIQUE (event_id, email)
);

ALTER TABLE users ALTER COLUMN name DROP NOT NULL;

-- Autenticacion. Nullable: ya hay filas de usuarios creadas antes de que existiera login.
ALTER TABLE users ADD COLUMN IF NOT EXISTS password VARCHAR(100);

CREATE TABLE IF NOT EXISTS user_role (
    user_id     INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role        VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE IF NOT EXISTS favorite (
    user_id     INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_id    INTEGER NOT NULL REFERENCES event(id) ON DELETE CASCADE,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, event_id)
);

-- DEFAULT TRUE es a proposito: solo afecta el backfill de las cuentas que ya
-- existian (esta linea corre una unica vez, la primera vez que se agrega la
-- columna). De ahi en mas, UserServiceImpl.register() siempre lo pisa a false
-- a mano antes de mandar el mail de confirmacion: una cuenta nueva SI tiene
-- que confirmar el mail.
ALTER TABLE users ADD COLUMN IF NOT EXISTS verified BOOLEAN NOT NULL DEFAULT TRUE;

-- Un solo link a la vez por (usuario, proposito): antes de crear uno nuevo se
-- borran los anteriores (ver VerificationTokenDao.deleteByUserAndPurpose).
CREATE TABLE IF NOT EXISTS verification_token (
    token       VARCHAR(64) PRIMARY KEY,
    user_id     INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    purpose     VARCHAR(20) NOT NULL
                    CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD')),
    expires_at  TIMESTAMP NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

-- Una reseña por (evento, usuario): solo se puede dejar una vez que el evento
-- termino y solo si estuvo anotado (ver ReviewServiceImpl), y no se puede editar
-- despues de mandada.
CREATE TABLE IF NOT EXISTS review (
    id          SERIAL PRIMARY KEY,
    event_id    INTEGER NOT NULL REFERENCES event(id) ON DELETE CASCADE,
    user_id     INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rating      INTEGER NOT NULL CHECK (rating BETWEEN 0 AND 5),
    comment     VARCHAR(1000),
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (event_id, user_id)
);


-- Lista de espera. Cuando se libera un cupo, al primero de la lista se le manda
-- un link unico con un token: el token ES la identificacion (puede abrirlo sin
-- estar logueado, o logueado con otra cuenta). Vence a las 24hs o cuando empieza
-- el evento, lo que pase antes.
--
-- Solo existen dos estados. Si la persona rechaza, o deja vencer la invitacion,
-- se borra su fila de registration y esta se va con ella por el ON DELETE
-- CASCADE: sale de la lista y, si quiere, puede volver a anotarse ultima.
--
-- El UNIQUE sobre registration_id es la regla "una invitacion por inscripcion":
-- es lo que hace que nunca se llame dos veces a la misma persona.
--
-- Al CHECK se le pone nombre a proposito: sin nombre, Postgres y HSQLDB le
-- inventan uno distinto cada uno y despues no hay ALTER que sirva en los dos.
CREATE TABLE IF NOT EXISTS waitlist_invitation (
    id               SERIAL PRIMARY KEY,
    registration_id  INTEGER NOT NULL UNIQUE REFERENCES registration(id) ON DELETE CASCADE,
    token            VARCHAR(64) NOT NULL UNIQUE,
    status           VARCHAR(20) DEFAULT 'PENDING' NOT NULL,
    sent_at          TIMESTAMP NOT NULL DEFAULT now(),
    expires_at       TIMESTAMP NOT NULL,
    CONSTRAINT waitlist_invitation_status_ck CHECK (status IN ('PENDING', 'ACCEPTED'))
);

-- La consulta "quien sigue en la lista de espera de este evento" corre en cada
-- cancelacion. Filtra por event_id y status, y ordena por registered_at: las
-- tres columnas del indice, en ese orden.
CREATE INDEX IF NOT EXISTS idx_registration_waitlist
    ON registration (event_id, status, registered_at);
