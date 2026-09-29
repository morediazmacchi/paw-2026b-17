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
    price           NUMERIC(10,2) DEFAULT 0 NOT NULL
                        CHECK (price >= 0),
    max_capacity    INTEGER
                        CHECK (max_capacity IS NULL OR max_capacity > 0),
    creator_email   VARCHAR(150) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

-- Un evento puede tener varias fotos (antes era 1:1, con una columna
-- image_id en event; ver postgres-migrations.sql para la migracion de la
-- base vieja que todavia la tiene). El orden de galeria es el orden de
-- insercion (id ascendente): la primera es la portada, la que se usa en
-- las tarjetas/listados (ver Event.getImageId() en el modelo, que la
-- deriva de la lista en vez de tener su propia columna). Sin FK a image.id,
-- mismo criterio que la columna vieja: referencia suelta a proposito.
CREATE TABLE IF NOT EXISTS event_image (
    id          SERIAL PRIMARY KEY,
    event_id    INTEGER NOT NULL REFERENCES event(id) ON DELETE CASCADE,
    image_id    INTEGER NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

-- Un evento puede tener varias categorias (antes era 1:1, con una columna
-- category_id en event; ver postgres-migrations.sql para la migracion de la
-- base vieja que todavia la tiene). extra_info es texto libre y opcional: lo
-- que el creador cuenta especificamente de ESA categoria para ESE evento (ej.
-- que artistas van a tocar si la categoria es Arte, que bandas si es Musica).
-- No hay una pregunta distinta por categoria a proposito: el set de
-- categorias es fijo y chico (ver data.sql), no hace falta un formulario
-- dinamico para esto.
CREATE TABLE IF NOT EXISTS event_category (
    event_id     INTEGER NOT NULL REFERENCES event(id) ON DELETE CASCADE,
    category_id  INTEGER NOT NULL REFERENCES category(id),
    extra_info   VARCHAR(500),
    PRIMARY KEY (event_id, category_id)
);

-- PENDING: la inscripcion espera que el creador la acepte o la rechace
-- (evento con requires_approval). REJECTED es un estado terminal: el
-- creador la rechazo (ver RegistrationServiceImpl.reject()).
CREATE TABLE IF NOT EXISTS registration (
    id                SERIAL PRIMARY KEY,
    event_id          INTEGER NOT NULL REFERENCES event(id) ON DELETE CASCADE,
    email             VARCHAR(150) NOT NULL,
    name              VARCHAR(150),
    registered_at     TIMESTAMP NOT NULL DEFAULT now(),
    status            VARCHAR(20) DEFAULT 'CONFIRMED' NOT NULL
                          CHECK (status IN ('CONFIRMED', 'WAITLISTED', 'PENDING', 'REJECTED')),
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

-- Idioma elegido (ej. "es"/"en"), para no depender solo de la cookie del
-- navegador (LocaleController/WebConfig.localeResolver) y para saber en que
-- idioma mandarle los mails (ver NotificationServiceImpl). NULL = todavia no
-- eligio ninguno (cuentas viejas, o nunca toco el boton de idioma): en ese
-- caso se usa el idioma de la cookie/navegador para la pagina, y el default
-- (castellano) para los mails.
ALTER TABLE users ADD COLUMN IF NOT EXISTS preferred_locale VARCHAR(5);

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


-- Lista de espera: no tiene tabla propia. Es simplemente registration con status
-- WAITLISTED, ordenada por registered_at. Cuando se libera un lugar, el primero
-- pasa directo a CONFIRMED (ver RegistrationServiceImpl.promoteFromWaitlist()).
-- Las bases viejas pueden conservar la tabla waitlist_invitation del esquema
-- anterior (invitacion con 24hs para aceptar): ya nadie la lee ni la escribe.

-- La consulta "quien sigue en la lista de espera de este evento" corre en cada
-- cancelacion. Filtra por event_id y status, y ordena por registered_at: las
-- tres columnas del indice, en ese orden.
CREATE INDEX IF NOT EXISTS idx_registration_waitlist
    ON registration (event_id, status, registered_at);

-- Eventos con confirmacion manual del organizador. Si esta en true, una
-- inscripcion nueva no nace CONFIRMED sino PENDING: el creador la tiene
-- que aceptar o rechazar el mismo (ver RegistrationServiceImpl.register()).
ALTER TABLE event ADD COLUMN IF NOT EXISTS requires_approval BOOLEAN NOT NULL DEFAULT false;

-- PENDING (esperando que el creador decida) y REJECTED (el creador la
-- rechazo) se suman a los valores posibles de status. Los 4 estados se guardan
-- en MAYUSCULA (RegistrationStatus.name(), ver postgres-migrations.sql). El CHECK de mas arriba
-- (en el CREATE TABLE de "registration") ya nace con los 4 valores para
-- cualquier base nueva (HSQLDB de los tests, o un Postgres recien creado).
--
-- Para el Postgres que YA existe (que tiene el CHECK viejo, de antes de este
-- cambio, solo con 'confirmed'/'waitlisted'), la migracion que lo reemplaza
-- vive aparte, en postgres-migrations.sql: ese constraint quedo sin nombre
-- en el CREATE TABLE original, y ALTER TABLE ... DROP CONSTRAINT IF EXISTS
-- es sintaxis de Postgres que HSQLDB no entiende (lo comprobamos a mano: tira
-- error de parseo, no de "no existe"), asi que no puede vivir en este mismo
-- archivo, que corren los dos motores.

-- Eventos privados. Sin CHECK a proposito (mismo criterio que neighborhood,
-- unas lineas mas arriba: son valores fijos de un enum de Java, EventVisibility,
-- y esta columna no necesita filtrar por SQL crudo sobre su valor como si
-- necesita registration.status para la lista de espera). Guarda el nombre
-- de la constante tal cual (PUBLIC/PRIVATE, en mayusculas), mismo criterio
-- que neighborhood con Barrio: siempre EventVisibility.name()/.valueOf().
--
-- No aparece en /eventos ni en las busquedas (ver EventJdbcDao.buildWhereClause):
-- solo lo ve su creador, quien ya este anotado, o quien tenga una invitacion
-- valida (ver event_invitation, mas abajo).
ALTER TABLE event ADD COLUMN IF NOT EXISTS visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC';

-- Invitacion personal a un evento privado. A diferencia de waitlist_invitation
-- no hay "aceptar o rechazar": esto solo desbloquea poder VER el evento (ver
-- EventController.canView); inscribirse de ahi en mas sigue el camino normal
-- de RegistrationServiceImpl.register(), con sus mismas reglas de cupo y de
-- confirmacion del organizador si el evento las tiene.
--
-- Ya NO hay un token que funcione como credencial por si solo (ver mas abajo,
-- "ya no se usa mas token"): quien entra tiene que SER la persona invitada,
-- logueada con la cuenta que quedo enlazada (event_invitation.user_id).
--
-- UNIQUE(event_id, email) evita mandarle dos invitaciones (y dos mails) a la
-- misma persona para el mismo evento.
CREATE TABLE IF NOT EXISTS event_invitation (
    id          SERIAL PRIMARY KEY,
    event_id    INTEGER NOT NULL REFERENCES event(id) ON DELETE CASCADE,
    email       VARCHAR(150) NOT NULL,
    invited_by  VARCHAR(150) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (event_id, email)
);

-- Soft-delete de un evento: NULL = activo, con fecha = cancelado por su
-- creador (ver EventDao.cancel()). No se borra la fila (ON DELETE CASCADE de
-- "registration" arrastraria las inscripciones, y quien ya estaba anotado
-- tiene que poder seguir viendolo en "Mis inscripciones"/el detalle).
-- EventJdbcDao.search()/findAll()/findByCategory() lo excluyen; findById()/
-- findByCreatorEmail() no, a proposito.
ALTER TABLE event ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP;

-- Pin en el mapa (Leaflet + OpenStreetMap, ver publish.jsp/editar.jsp): el
-- creador elige a mano, de forma explicita, entre buscar la direccion contra
-- Nominatim (mapa de solo lectura, ver event-location-map.js) o cargarla el
-- mismo con un click. NULL en los dos = todavia no se puso ningun pin
-- (eventos viejos de antes de este cambio, o el creador no llego a
-- confirmar), y el detalle no muestra mapa en ese caso. NUMERIC(9,6): 3
-- enteros + 6 decimales, mas que suficiente para latitud (-90 a 90) y
-- longitud (-180 a 180) con precision de sobra.
ALTER TABLE event ADD COLUMN IF NOT EXISTS latitude NUMERIC(9,6);
ALTER TABLE event ADD COLUMN IF NOT EXISTS longitude NUMERIC(9,6);

-- true = el pin de arriba lo puso el creador a mano; false = lo puso
-- event-location-map.js al elegir una sugerencia de Nominatim. El detalle
-- del evento lo usa para avisar "puede no ser exacto" solo en el caso manual
-- (pasa sobre todo en parques/plazas grandes, donde Nominatim no siempre
-- puede geocodificar). DEFAULT true a proposito: todos los pines de antes de
-- esta columna se pusieron a mano (la unica forma que existia), asi que el
-- backfill los deja bien etiquetados sin necesidad de un UPDATE aparte.
ALTER TABLE event ADD COLUMN IF NOT EXISTS location_manual BOOLEAN NOT NULL DEFAULT true;

-- Info de la direccion que Nominatim no puede geocodificar (piso, depto, a
-- que timbre tocar, referencias): texto libre, nunca se manda a buscar en el
-- mapa, solo se muestra junto a la direccion en el detalle del evento. NULL =
-- nadie cargo nada (el campo es opcional).
ALTER TABLE event ADD COLUMN IF NOT EXISTS address_details VARCHAR(300);

-- Bandeja de mensajes (/perfil/bandeja): un aviso por cada cosa que le pasa a
-- alguien (se anoto, lo aceptaron, alguien se dio de baja de su evento...).
-- Complementa al mail, no lo reemplaza. Por email y no por user_id: a quien
-- invitaron a un evento privado puede no tener cuenta todavia (mismo criterio
-- que registration y event_invitation). type = InboxMessageType.name(), sin
-- CHECK a proposito (mismo criterio que event.visibility): los valores son de
-- un enum de Java y van a crecer con cada tarea nueva (preguntas, recordatorios).
-- read_at NULL = sin leer.
CREATE TABLE IF NOT EXISTS inbox_message (
    id               SERIAL PRIMARY KEY,
    recipient_email  VARCHAR(150) NOT NULL,
    type             VARCHAR(40)  NOT NULL,
    event_id         INTEGER REFERENCES event(id) ON DELETE CASCADE,
    actor_name       VARCHAR(150),
    body             VARCHAR(2000),
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    read_at          TIMESTAMP
);

-- La bandeja siempre se lee por destinatario y de la mas nueva a la mas vieja.
CREATE INDEX IF NOT EXISTS idx_inbox_recipient
    ON inbox_message (recipient_email, created_at);

-- Recordatorio "falta menos de 24 hs" (ver EventReminderScheduler): NULL = todavia
-- no se mando. Sirve para no mandarlo dos veces: la tarea lo "reclama" con un
-- UPDATE ... WHERE reminder_sent_at IS NULL, y solo quien lo reclamo manda los mails.
ALTER TABLE event ADD COLUMN IF NOT EXISTS reminder_sent_at TIMESTAMP;

-- Preguntas y respuestas de un evento. answer_text NULL = todavia sin responder.
-- Se responde con UPDATE ... WHERE answer_text IS NULL: una sola respuesta, nunca se pisa.
CREATE TABLE IF NOT EXISTS event_question (
    id            SERIAL PRIMARY KEY,
    event_id      INTEGER NOT NULL REFERENCES event(id) ON DELETE CASCADE,
    user_id       INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    question_text VARCHAR(300) NOT NULL,
    answer_text   VARCHAR(300),
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    answered_at   TIMESTAMP
);

-- Si el evento cuenta con estacionamiento para los asistentes. Checkbox
-- comun (mismo criterio que requires_approval): ausente en el form = false.
ALTER TABLE event ADD COLUMN IF NOT EXISTS has_parking BOOLEAN NOT NULL DEFAULT false;

-- Migracion de creator_email/email/invited_by (String) a una FK de verdad
-- contra users.id. Nullable a proposito (no se puede agregar NOT NULL a una
-- columna nueva sobre una tabla que ya tiene filas sin romper el arranque):
-- postgres-migrations.sql la backfillea contra la base real; de aca en mas
-- el codigo Java siempre la completa (viene resuelta del Principal
-- autenticado, ver EventController/ProfileController), asi que en la
-- practica nunca queda NULL para una fila nueva. Las columnas viejas
-- (creator_email/email/invited_by) se quedan por ahora: todavia las lee
-- NotificationService (le alcanza con Event/Registration/EventInvitation
-- enteros, nunca las toca directo) y sirven de respaldo mientras esto
-- termina de asentarse. Sacarlas es una tarea aparte, mas adelante.
ALTER TABLE event ADD COLUMN IF NOT EXISTS creator_id INTEGER REFERENCES users(id);
ALTER TABLE registration ADD COLUMN IF NOT EXISTS user_id INTEGER REFERENCES users(id);
ALTER TABLE event_invitation ADD COLUMN IF NOT EXISTS invited_by_user_id INTEGER REFERENCES users(id);

-- Grupo B: a quien invitan a un evento privado (event_invitation.email) o le
-- llega un aviso a la bandeja (inbox_message.recipient_email) puede no tener
-- cuenta todavia -- a diferencia de creator_id/registration.user_id de arriba,
-- que siempre tienen un User real detras. Por eso esta FK es la unica fuente
-- optimista: se completa SI YA existe una cuenta en el momento de invitar/
-- avisar (ver EventInvitationServiceImpl.invite()/InboxServiceImpl.add()), y
-- si no existe, se reconcilia sola despues, cuando esa persona se registra
-- (ver UserServiceImpl.register(), que linkea por email todo lo que haya
-- quedado con user_id NULL). El email se queda siendo el dato de verdad: un
-- user_id en NULL para siempre es un caso normal, no un error.
ALTER TABLE event_invitation ADD COLUMN IF NOT EXISTS user_id INTEGER REFERENCES users(id);
ALTER TABLE inbox_message ADD COLUMN IF NOT EXISTS user_id INTEGER REFERENCES users(id);

-- Suspension de cuenta por un admin (ver UserService.suspend()/ModerationService).
-- UserDetailsServiceImpl la trata igual que "verified=false": no puede loguearse
-- hasta que un admin la reactive (ver UserService.reactivate()). false por
-- default: ninguna cuenta vieja queda suspendida por accidente al agregar la columna.
ALTER TABLE users ADD COLUMN IF NOT EXISTS suspended BOOLEAN NOT NULL DEFAULT false;

-- Baja de un evento por moderacion (ver ModerationService.removeEvent()). Reusa
-- cancelled_at (arriba) como el unico flag de "este evento ya no esta activo":
-- asi todo lo que ya lo respeta (busquedas, "editar", inscribirse, preguntas)
-- automaticamente tambien excluye a un evento removido por un admin, sin
-- duplicar esa logica en cada lugar. Estas dos columnas solo agregan el motivo,
-- para poder avisarle al creador y mostrarle un texto distinto al de "vos lo
-- cancelaste" (ver EventJdbcDao.removeByAdmin()).
ALTER TABLE event ADD COLUMN IF NOT EXISTS removed_by_admin BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE event ADD COLUMN IF NOT EXISTS removed_reason VARCHAR(500);

-- Reporte de un evento (ver ModerationService): lo puede hacer cualquier
-- usuario logueado (admins incluidos), y lo revisa despues un admin, que lo
-- descarta o da de baja el evento (ver AdminController). No se borra nunca:
-- status distinto de PENDING una vez resuelto es el historial.
CREATE TABLE IF NOT EXISTS event_report (
    id                 SERIAL PRIMARY KEY,
    event_id           INTEGER NOT NULL REFERENCES event(id) ON DELETE CASCADE,
    reporter_user_id   INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason             VARCHAR(500) NOT NULL,
    created_at         TIMESTAMP NOT NULL DEFAULT now(),
    status             VARCHAR(20) DEFAULT 'PENDING' NOT NULL
                           CHECK (status IN ('PENDING', 'DISMISSED', 'ACTIONED')),
    resolved_by        INTEGER REFERENCES users(id),
    resolved_at        TIMESTAMP
);

-- La cola de moderacion siempre se lee "los PENDING, mas viejo primero" (FIFO).
CREATE INDEX IF NOT EXISTS idx_event_report_pending
    ON event_report (status, created_at);

-- Cambio de intencion de diseño: ya no se usa mas token como credencial de la
-- invitacion (esa idea era de antes de tener event_invitation.user_id mas
-- arriba). Quien abre el link tiene que estar logueado con la cuenta invitada
-- (ver EventController.canView()/EventInvitationService.isInvited()); el
-- token que alguna vez se genero y guardo ya no lo lee nadie. El DROP COLUMN
-- de verdad vive en postgres-migrations.sql: HSQLDB (los tests) no entiende
-- "IF EXISTS" en un DROP COLUMN, y de todas formas una base de tests nace de
-- cero sin esta columna (el CREATE TABLE de aca arriba ya no la declara).
