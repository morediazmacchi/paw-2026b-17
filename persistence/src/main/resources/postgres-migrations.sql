-- Migraciones que SOLO corren contra Postgres real (ver RootConfig: TestConfig,
-- que arma la base HSQLDB en memoria de los tests, no carga este archivo).
--
-- Aca va lo que schema.sql no puede tener porque lo corren los dos motores y
-- HSQLDB no entiende la sintaxis de Postgres. Se ejecuta en CADA arranque, asi
-- que tiene que ser idempotente Y barato: nada de agregar y sacar una columna
-- o un constraint para que "no rompa" la segunda vez. Postgres no libera las
-- columnas borradas (cada una sigue contando para el tope de 1600 por tabla),
-- y event llego a ese tope: la app dejo de levantar.
--
-- Ya corrieron y se sacaron de aca (el Postgres real quedo migrado, verificado
-- a mano; una base nueva nace bien con schema.sql):
--   - registration.status en MAYUSCULA + su CHECK de 4 estados
--   - event.category_id / event.image_id -> event_category / event_image
--   - event_invitation.token, tabla waitlist_invitation, mensajes de bandeja viejos
--   - CHECK de event_report.status y columnas changes_requested_*
--   - FK y UNIQUE de event_image.image_id
--   - columnas de fecha TIMESTAMP -> TIMESTAMP WITH TIME ZONE
-- Una base vieja que todavia tenga esas cosas hay que migrarla con una version
-- anterior de este archivo (git log -- este archivo).

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

-- Invitados sin cuenta (ver schema.sql): backfill "optimista" para invitados/destinatarios
-- que YA tenian cuenta antes de este cambio. Quien todavia no tenia cuenta en
-- ese momento sigue en NULL aca -- se completa solo, desde Java, el dia que
-- esa persona se registre (ver UserServiceImpl.register()).
UPDATE event_invitation ei SET user_id = u.id
FROM users u
WHERE LOWER(u.email) = LOWER(ei.email) AND ei.user_id IS NULL;

UPDATE inbox_message im SET user_id = u.id
FROM users u
WHERE LOWER(u.email) = LOWER(im.recipient_email) AND im.user_id IS NULL;

-- Roles como enum: en user_role se guarda el nombre del enum Role
-- (USER / ADMIN / SUPERADMIN); el prefijo "ROLE_" lo agrega Role.getAuthority() para
-- Spring Security. Idempotente y barato: solo toca las filas que todavia tienen el prefijo.
UPDATE user_role SET role = SUBSTRING(role FROM 6) WHERE role LIKE 'ROLE\_%';
