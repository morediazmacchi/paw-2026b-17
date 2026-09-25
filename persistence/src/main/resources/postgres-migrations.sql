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
-- InvitationStatus, EventVisibility y Barrio). Antes eran minuscula.
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
