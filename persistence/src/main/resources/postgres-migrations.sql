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
-- son tres ALTER TABLE simples, ninguno con nada adentro que lleve ";".
ALTER TABLE registration DROP CONSTRAINT IF EXISTS registration_status_check;
ALTER TABLE registration DROP CONSTRAINT IF EXISTS registration_status_ck;
ALTER TABLE registration ADD CONSTRAINT registration_status_ck
    CHECK (status IN ('confirmed', 'waitlisted', 'pending', 'rejected'));
