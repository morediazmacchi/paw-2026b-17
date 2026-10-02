-- Migraciones que SOLO corren contra Postgres real (ver
-- WebConfig.databasePopulator(); TestConfig, que arma la base HSQLDB en
-- memoria de los tests, no carga este archivo).
--
-- Por que existe aparte de schema.sql: schema.sql lo corren los dos motores
-- (Postgres real y el HSQLDB en memoria de los tests), asi que solo puede
-- tener SQL que los dos entiendan. Esta migracion no entra ahi porque:
--   - El Postgres que ya esta corriendo tiene, desde antes de este cambio,
--     un CHECK sin nombre en registration.status que solo permitia
--     'confirmed'/'waitlisted'. Postgres lo bautizo solo como
--     "registration_status_check" (confirmado a mano con \d registration).
--     Hay que reemplazarlo por uno que tambien deje 'pending'/'rejected'.
--   - HSQLDB nunca tiene ese problema: en los tests la tabla se crea de cero
--     en cada corrida, y el CREATE TABLE de schema.sql ya nace con los 4
--     valores. No hay nada que migrar ahi.
--   - "ALTER TABLE ... DROP CONSTRAINT IF EXISTS" es sintaxis de Postgres.
--     Se probo a mano contra HSQLDB (hsqldb 2.7.3, sql.syntax_pgs=true) y
--     tira error de parseo ("object not found: IF"): no es que HSQLDB no
--     encuentre el constraint, es que ni siquiera entiende la clausula
--     "IF EXISTS" en un DROP CONSTRAINT. Por eso no puede compartir archivo
--     con schema.sql.
--
-- Las tres partes son idempotentes (se puede correr este archivo mil veces,
-- en cada arranque de la app, sin romper nada): los tres DROP tienen su
-- propio IF EXISTS, asi que la segunda vez que corre (cuando el viejo ya no
-- esta y el nuevo ya esta puesto) los dos primeros no hacen nada y el ADD
-- reemplaza al que el tercer DROP acaba de sacar.
--
-- Nota: se probo primero con un bloque DO ... EXCEPTION (el idiom habitual de
-- Postgres para "ADD CONSTRAINT IF NOT EXISTS", que no existe como tal) pero
-- ResourceDatabasePopulator separa el script en sentencias cortando por ";"
-- sin entender el dollar-quoting ($$) de Postgres: partia el bloque DO a la
-- mitad y tiraba "Unterminated dollar quote". Por eso el camino de abajo, que
-- son sentencias simples, ninguna con nada adentro que lleve ";".
--
-- Estados en MAYUSCULA (RegistrationStatus.name()/valueOf(), mismo criterio que
-- EventVisibility y Barrio). Antes eran minuscula.
-- OJO, el orden importa:
--   1. Los DROP primero: mientras este el CHECK viejo (solo minuscula), el
--      UPDATE a mayuscula lo violaria.
--   2. El UPDATE solo toca las filas que todavia estan en minuscula: la primera
--      vez migra los datos, de ahi en mas no hace nada.
--   3. El ADD al final, ya con los datos en mayuscula.
-- Este bloque REEMPLAZA al que ponia el CHECK en minuscula (no se agrega otro
-- al final): si quedaran los dos, en el segundo arranque el viejo intentaria
-- poner el CHECK en minuscula sobre datos en mayuscula y la app no levantaria.
-- Consecuencia: un WAR de antes de este cambio ya no sirve contra esta base
-- (su migracion choca con los datos en mayuscula). Hacer pull antes de deployar.
ALTER TABLE registration DROP CONSTRAINT IF EXISTS registration_status_check;
ALTER TABLE registration DROP CONSTRAINT IF EXISTS registration_status_ck;
UPDATE registration SET status = UPPER(status) WHERE status <> UPPER(status);
ALTER TABLE registration ALTER COLUMN status SET DEFAULT 'CONFIRMED';
ALTER TABLE registration ADD CONSTRAINT registration_status_ck
    CHECK (status IN ('CONFIRMED', 'WAITLISTED', 'PENDING', 'REJECTED'));

-- Categorias multiples por evento (evento N:M categoria, ver event_category
-- en schema.sql). El Postgres real que ya esta corriendo tiene la columna
-- vieja event.category_id (NOT NULL): schema.sql define la tabla event SIN
-- esa columna, asi que en una base nueva jamas se crea, pero en la base vieja
-- "CREATE TABLE IF NOT EXISTS" es un no-op sobre una tabla que ya existe y la
-- columna se queda ahi.
--
-- Mismo motivo que la migracion de arriba para no usar un bloque DO: el
-- ResourceDatabasePopulator corta por ";" sin entender dollar-quoting, asi
-- que tiene que ser SQL plano. El ADD COLUMN IF NOT EXISTS de la primera
-- linea es lo que permite que esto sea idempotente sin DO: la primera vez que
-- corre (con la columna vieja todavia puesta) migra los datos reales y la
-- saca; de ahi en mas, en cada arranque siguiente, la vuelve a crear vacia
-- (nullable, sin filas que migrar: el INSERT no hace nada) y la vuelve a
-- sacar en el mismo arranque. Ni se pierde nada ni rompe contra una columna
-- inexistente.
ALTER TABLE event ADD COLUMN IF NOT EXISTS category_id INTEGER;

INSERT INTO event_category (event_id, category_id)
SELECT e.id, e.category_id FROM event e
WHERE e.category_id IS NOT NULL
ON CONFLICT (event_id, category_id) DO NOTHING;

ALTER TABLE event DROP COLUMN IF EXISTS category_id;

-- Varias fotos por evento (evento 1:N imagen, ver event_image en schema.sql).
-- Mismo mecanismo e idempotencia que la migracion de arriba: event_image no
-- tiene una clave compuesta como event_category (su PK es un id propio), asi
-- que ON CONFLICT no aplica aca, pero no hace falta: despues de la primera
-- corrida (la que migra los datos reales), la columna que ADD COLUMN IF NOT
-- EXISTS vuelve a crear en cada arranque siguiente nace vacia -- el SELECT no
-- devuelve filas y el INSERT no duplica nada.
ALTER TABLE event ADD COLUMN IF NOT EXISTS image_id INTEGER;

INSERT INTO event_image (event_id, image_id)
SELECT e.id, e.image_id FROM event e
WHERE e.image_id IS NOT NULL;

ALTER TABLE event DROP COLUMN IF EXISTS image_id;

-- Backfill de creator_id/user_id/invited_by_user_id (ver schema.sql: las tres
-- columnas nacen NULL en la base real, recien agregadas). Se resuelven contra
-- users por email, sin distinguir mayusculas/minusculas (asi se comparan en
-- Java: normalizedEmail = email.trim().toLowerCase()). Idempotente: solo
-- toca las filas que todavia estan sin completar (IS NULL), asi que correrlo
-- de nuevo en cada arranque no pisa nada ya resuelto.
--
-- Si alguna fila vieja no encuentra un user con ese email (no deberia pasar:
-- crear un evento o anotarse siempre paso por userService.findOrCreate en su
-- momento, asi que services todo email de creator_email/registration.email
-- tiene su fila en users) queda en NULL sin romper nada: las columnas viejas
-- (creator_email/email/invited_by) se quedan intactas como respaldo.
UPDATE event e SET creator_id = u.id
FROM users u
WHERE LOWER(u.email) = LOWER(e.creator_email) AND e.creator_id IS NULL;

UPDATE registration r SET user_id = u.id
FROM users u
WHERE LOWER(u.email) = LOWER(r.email) AND r.user_id IS NULL;

UPDATE event_invitation ei SET invited_by_user_id = u.id
FROM users u
WHERE LOWER(u.email) = LOWER(ei.invited_by) AND ei.invited_by_user_id IS NULL;

-- Grupo B (ver schema.sql): backfill "optimista" para invitados/destinatarios
-- que YA tenian cuenta antes de este cambio. Quien todavia no tenia cuenta en
-- ese momento sigue en NULL aca -- se completa solo, desde Java, el dia que
-- esa persona se registre (ver UserServiceImpl.register()).
UPDATE event_invitation ei SET user_id = u.id
FROM users u
WHERE LOWER(u.email) = LOWER(ei.email) AND ei.user_id IS NULL;

UPDATE inbox_message im SET user_id = u.id
FROM users u
WHERE LOWER(u.email) = LOWER(im.recipient_email) AND im.user_id IS NULL;

-- Cambio de intencion de diseño (ver el comentario de event_invitation en
-- schema.sql): la invitacion ya no se abre con un token que valga por si
-- solo, ahora hay que estar logueado con la cuenta invitada
-- (event_invitation.user_id, backfillado arriba). El token que se llegaron a
-- generar y guardar antes de este cambio ya no lo lee nadie del lado de Java.
-- Mismo motivo que el DROP CONSTRAINT de mas arriba para no compartir archivo
-- con schema.sql: se probo "ALTER TABLE ... DROP COLUMN IF EXISTS" contra
-- HSQLDB y tira el mismo error de parseo ("object not found: IF") -- no
-- entiende la clausula "IF EXISTS" ahi tampoco. Una base de tests nunca tuvo
-- esta columna (el CREATE TABLE de schema.sql ya no la declara), asi que no
-- hay nada que migrar en ese lado.
ALTER TABLE event_invitation DROP COLUMN IF EXISTS token;

-- Panel de seguimiento de reportes (ver ModerationService.requestChanges()):
-- el admin puede pedirle cambios al creador en vez de descartar el reporte o
-- dar de baja el evento directo. Mismo motivo que el resto de este archivo
-- para el CHECK: el Postgres que ya esta corriendo tiene un CHECK sin nombre
-- ("event_report_status_check", confirmado con \d event_report) que solo
-- permite 'PENDING'/'DISMISSED'/'ACTIONED'. HSQLDB no tiene este problema: la
-- tabla nace de cero en cada corrida de tests con el CHECK ya actualizado
-- (ver schema.sql).
ALTER TABLE event_report DROP CONSTRAINT IF EXISTS event_report_status_check;
ALTER TABLE event_report ADD CONSTRAINT event_report_status_check
    CHECK (status IN ('PENDING', 'CHANGES_REQUESTED', 'DISMISSED', 'ACTIONED'));

ALTER TABLE event_report ADD COLUMN IF NOT EXISTS changes_requested_message VARCHAR(1000);
ALTER TABLE event_report ADD COLUMN IF NOT EXISTS changes_requested_at TIMESTAMP;
ALTER TABLE event_report ADD COLUMN IF NOT EXISTS changes_requested_by INTEGER REFERENCES users(id);

-- FK + UNIQUE en event_image.image_id (ver schema.sql: la tabla nace con las
-- dos desde el principio en una base nueva/HSQLDB, pero la base real ya tiene
-- event_image sin ninguna). Mismo motivo que el resto del archivo para no
-- compartir esto con schema.sql: "ADD CONSTRAINT IF NOT EXISTS" no existe en
-- Postgres, asi que va el mismo idiom de DROP CONSTRAINT IF EXISTS + ADD.
ALTER TABLE event_image DROP CONSTRAINT IF EXISTS event_image_image_id_fk;
ALTER TABLE event_image ADD CONSTRAINT event_image_image_id_fk
    FOREIGN KEY (image_id) REFERENCES image(id) ON DELETE CASCADE;

ALTER TABLE event_image DROP CONSTRAINT IF EXISTS event_image_image_id_uq;
ALTER TABLE event_image ADD CONSTRAINT event_image_image_id_uq UNIQUE (image_id);

-- Limpieza de restos del sistema viejo de lista de espera (invitacion con 24hs
-- para aceptar/rechazar, reemplazado por promoteFromWaitlist(): quien sale de
-- la espera pasa confirmado/pendiente directo, sin tener que aceptar nada).
-- Primero las filas de bandeja: InboxMessageType ya no tiene estas 3 constantes
-- (WAITLIST_SPOT_OFFERED/ACCEPTED/DECLINED, nunca se vuelven a generar), asi que
-- InboxMessageJdbcDao.valueOf() reventaria con un 500 al leer la bandeja de
-- quien tenga alguna fila vieja con ese type. Idempotente: la segunda vez que
-- corre no encuentra ninguna fila con esos types y no hace nada.
DELETE FROM inbox_message WHERE type IN ('WAITLIST_SPOT_OFFERED', 'WAITLIST_ACCEPTED', 'WAITLIST_DECLINED');

-- La tabla en si, del esquema anterior a promoteFromWaitlist(): ya nadie la lee
-- ni la escribe (ver schema.sql). IF EXISTS la hace idempotente.
DROP TABLE IF EXISTS waitlist_invitation;

-- Columnas de fecha/hora sin zona horaria -> WITH TIME ZONE (ver schema.sql,
-- donde ya nacen asi para una base nueva/HSQLDB). El USING de cada ALTER es
-- "columna::timestamptz", nunca "columna AT TIME ZONE 'algo'": ese cast simple
-- es lo que lo hace idempotente sin DO block (mismo motivo que el resto del
-- archivo para no usar DO, ver comentario de arriba de todo). La primera vez
-- que corre, columna es TIMESTAMP (sin tz): Postgres interpreta cada valor
-- existente en la zona horaria de la sesion (la misma que ya se usaba para
-- mostrarlo), asi que no cambia ningun dato visible, solo lo vuelve inequivoco
-- de ahi en mas. La segunda vez que corre, columna ya es TIMESTAMPTZ: el mismo
-- cast ahi es un no-op (timestamptz::timestamptz), no reinterpreta nada.
ALTER TABLE image ALTER COLUMN uploaded_at TYPE TIMESTAMP WITH TIME ZONE USING uploaded_at::timestamptz;
ALTER TABLE event ALTER COLUMN event_date TYPE TIMESTAMP WITH TIME ZONE USING event_date::timestamptz;
ALTER TABLE event ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at::timestamptz;
ALTER TABLE event ALTER COLUMN cancelled_at TYPE TIMESTAMP WITH TIME ZONE USING cancelled_at::timestamptz;
ALTER TABLE event ALTER COLUMN reminder_sent_at TYPE TIMESTAMP WITH TIME ZONE USING reminder_sent_at::timestamptz;
ALTER TABLE event ALTER COLUMN featured_at TYPE TIMESTAMP WITH TIME ZONE USING featured_at::timestamptz;
ALTER TABLE event_image ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at::timestamptz;
ALTER TABLE registration ALTER COLUMN registered_at TYPE TIMESTAMP WITH TIME ZONE USING registered_at::timestamptz;
ALTER TABLE favorite ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at::timestamptz;
ALTER TABLE verification_token ALTER COLUMN expires_at TYPE TIMESTAMP WITH TIME ZONE USING expires_at::timestamptz;
ALTER TABLE verification_token ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at::timestamptz;
ALTER TABLE review ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at::timestamptz;
ALTER TABLE event_invitation ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at::timestamptz;
ALTER TABLE inbox_message ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at::timestamptz;
ALTER TABLE inbox_message ALTER COLUMN read_at TYPE TIMESTAMP WITH TIME ZONE USING read_at::timestamptz;
ALTER TABLE event_question ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at::timestamptz;
ALTER TABLE event_question ALTER COLUMN answered_at TYPE TIMESTAMP WITH TIME ZONE USING answered_at::timestamptz;
ALTER TABLE event_report ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at::timestamptz;
ALTER TABLE event_report ALTER COLUMN resolved_at TYPE TIMESTAMP WITH TIME ZONE USING resolved_at::timestamptz;
ALTER TABLE event_report ALTER COLUMN changes_requested_at TYPE TIMESTAMP WITH TIME ZONE USING changes_requested_at::timestamptz;
