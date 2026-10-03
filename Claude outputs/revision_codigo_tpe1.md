# Revisión de código — Plancito (paw-2026b-17) — TPE1

Fecha: 02/10/2026 · Entrega: 05/10, 19:00 (demo + code review / defensa oral)
Alcance: los 6 módulos (model, persistence-contracts, persistence, services-contracts, services, webapp), poms, `web.xml`, SQL, JSP/tags/JS/CSS e i18n. Todo leído sobre una copia de tu repo (commit `3fa5390`); no toqué nada de tu repo.

## Cómo leer esto

Cada hallazgo lleva una etiqueta de origen:

- **[Sotuyo]** lo dijo en clase; te pongo la clase y, cuando puedo, la línea de la transcripción (`texto-convertidoN.txt`).
- **[Mío]** es criterio mío (buenas prácticas, bugs, consistencia). Sotuyo no lo dijo; no lo tomes como "lo que va a pedir la cátedra".
- **[Cátedra-OK]** es algo que parece raro pero lo enseñó él (o su PDF), así que no se toca.

Severidad: **Alta** = arreglar antes del 05/10; **Media** = conviene y es barato; **Baja** = prolijidad / para después.

Cómo lo verifiqué: primero lectura completa del código (sobre una copia de tu repo, commit `3fa5390`; no toqué nada de tu repo). Después, para no quedarme con lo estático, levanté la app de verdad en mi entorno (Tomcat con JSP reales + PostgreSQL + un servidor de mail falso, nada sale a internet) y recorrí los flujos con requests HTTP reales, con un navegador y con tests de integración. Está resumido en la sección 10, incluido lo que **no** pude cubrir.

Los 644 tests unitarios/de persistencia corrieron verdes en mi entorno antes de tu commit; el bug de la sección 0 los pasa igual porque ningún test arma el request completo.

---

## 0. Bug crítico: `@ModelAttribute("currentUser")` pisa el usuario logueado — Alta, arreglar HOY [Mío]

**Qué pasa.** Desde el refactor de controllers del 01/10 (varios commits del grupo), los métodos de controller reciben el usuario así: `@ModelAttribute("currentUser") final User currentUser`. Spring toma ese objeto del modelo (lo arma `ErrorAdvice`), **pero además le bindea encima los parámetros del request**: variables de la URL (`{id}`) y campos de formulario. `User` tiene `setId`, `setName`, `setEmail`…, así que:

- En `POST /eventos/7/quiero-ir`, el `{id}` = 7 (el evento) queda como `currentUser.id`. La inscripción se guarda con `user_id = 7`, y la segunda persona que se anota recibe "ya estás anotado" porque el service busca por (evento, user_id) y encuentra la fila de la primera. Lo reproduje contra Postgres: cuatro personas se anotan una atrás de otra a un evento de cupo 2; la primera queda confirmada (con `user_id` = id del evento) y las otras tres reciben "ya estás anotado" sin que se cree su fila.
- El creador recibe **404 en su propio evento** al editar, cancelar, invitar, aceptar/rechazar/revocar: el chequeo "es el creador" compara contra el id ya pisado.
- `POST /perfil` con un campo `email` cambia el email de la cuenta en la base (`updateProfile(currentUser.getId(), nombre, currentUser.getEmail())` usa el email bindeado). Es decir, cualquiera logueado puede cambiarse el email sin verificarlo, aunque el input esté `disabled` en el JSP, porque se puede mandar a mano.
- Por el mismo mecanismo, todo handler que combine `{id}` en la URL con `currentUser.getId()` (favoritos, preguntas, reportes, reseñas) usa el id pisado. Estos cuatro no los medí uno por uno; los tres de arriba sí.

**Por qué probablemente no se notó.** Con pocos datos, el id de usuario y el de evento suelen coincidir (usuario 1, evento 1), y los tests de controller llaman al método directo, sin pasar por el binding de Spring.

**Arreglo** (verificado en mi copia: con esto, todos los flujos de las secciones 10 dan lo esperado). En los 6 controllers que lo usan (38 apariciones: `AdminController`, `EventController`, `EventQuestionController`, `EventRegistrationController`, `ProfileController`, `RegisterController`), cambiar:

```java
@ModelAttribute("currentUser") final User currentUser
// por
@ModelAttribute(value = "currentUser", binding = false) final User currentUser
```

(La definición en `ErrorAdvice` queda igual.) Alternativa más limpia y más "Sotuyo" (el controller no debería recibir una entidad bindeable): un `HandlerMethodArgumentResolver` para `User`, o resolver el `Principal` y pedir el usuario al service. Para el viernes, `binding = false` alcanza.

**Test de regresión sugerido** (MockMvc `standaloneSetup`, un test de 10 líneas): hacer `POST /eventos/999/quiero-ir` con un usuario de id 3 y verificar que la inscripción quedó con `userId == 3`, y `POST /perfil` con `email=otro@x.com` y verificar que el email no cambió.

**Datos ya contaminados.** Si alguien del grupo probó la app en Pampero o local después del 01/10, hay filas mal. Revisalo antes de la demo:

```sql
-- inscripciones con user_id que no corresponde a su email (se puede reparar)
SELECT r.id, r.event_id, r.email, r.user_id, u.id AS id_correcto
FROM registration r JOIN users u ON LOWER(u.email) = LOWER(r.email)
WHERE r.user_id IS DISTINCT FROM u.id;
-- reparación
UPDATE registration r SET user_id = u.id FROM users u
WHERE LOWER(u.email) = LOWER(r.email) AND r.user_id IS DISTINCT FROM u.id;
-- usuarios con el email cambiado a mano: mirá users.email vs. lo esperado
```

`favorite`, `event_question`, `review` y `event_report` no tienen columna de email, así que no se pueden reparar solas: lo más simple es borrar las filas de prueba creadas desde el 01/10 o recrear la base de la demo.

---

## 1. Lo que haría antes del 05/10 (en orden)

| # | Qué | Sev. | Esfuerzo |
|---|---|---|---|
| 0 | **`@ModelAttribute("currentUser")` bindea el `{id}` de la URL sobre el usuario: rompe anotarse, editar, cancelar, y permite cambiar el email** (sección 0) | **Alta** | 15 min + test |
| 1 | Credenciales reales en el repo (DB de Pampero, app password de Gmail) | Alta | 30 min + rotar |
| 2 | Superadmin sembrado con clave conocida, que además está escrita en `data.sql` | Alta | 10 min |
| 3 | Fotos de eventos privados se pueden bajar sin login (`/image/{id}`) | Alta | 30–60 min |
| 4 | `ErrorAdvice` captura `Exception` y convierte 405 / 400 en 500 | Media | 15 min |
| 5 | `register()` chequea antes de lockear y el controller "reintenta": **funciona** (lo probé con carreras reales, ver sección 10), queda como inconsistencia de diseño | Baja | 20 min |
| 6 | Código muerto confirmado (sección 4) — necesito tu OK | Media | 30 min |
| 7 | Líneas duplicadas por copy-paste en `ProfileController` y los dos `.bak` | Baja | 5 min |
| 8 | Claves i18n huérfanas y validadores custom sin traducción | Media | 30 min |
| 9 | Restos del sistema viejo de lista de espera (sección 4.1), incluido un mensaje de error que hoy dice algo falso | Media | 30–45 min |
| 10 | Carpeta `Claude outputs/` sin trackear en la raíz del repo: no hagas `git add .` | Baja | 1 min |
| 11 | Subida de imágenes: acepta cualquier archivo que diga `image/*` (SVG con script = XSS almacenado) (2.5) | Alta | 20 min |
| 12 | Usuario suspendido con sesión abierta sigue operando (2.4) | Media | 20 min |
| 13 | `SafeRedirect` se deja pasar `/\evil.com` (2.4) | Media | 5 min |
| 14 | Links de los mails apuntan a `http://localhost:8080` y el SMTP no tiene timeouts (2.7) | Media | 10 min |

El resto (duplicación grande en `NotificationServiceImpl`, `UserJdbcDao`, `ProfileController`) es refactor: valioso para la defensa si te preguntan, pero riesgoso para hacer en 3 días. Mi sugerencia está en la sección 9.

---

## 2. Seguridad

### 2.1 Credenciales en el repo — Alta [Mío]
- `services/src/main/resources/mail.properties` tiene usuario y app password reales de Gmail, trackeados desde el commit `3082639`.
- `WebConfig` tiene la contraseña de la base de Pampero en el bloque comentado, y está en el historial (commit `f6af3a4`).
- Borrarlas del archivo no alcanza: quedan en el historial de Bitbucket. Lo que realmente cierra el problema es **rotar** las dos (nueva app password en Google, nueva clave de la DB de Pampero).
- Cómo dejarlo bien: leer de variables de entorno o de un archivo externo no versionado (`mail.properties.example` en el repo, el real en `.gitignore`), y que `WebConfig` lea `DB_URL`/`DB_USER`/`DB_PASS` del entorno. Además elimina la "trampa" que ya estaba en tu triage del 08/09: dejar `WebConfig` en modo Pampero y correr local escribiendo en producción.
- Reescribir el historial (`git filter-repo`) es opcional y lo tiene que acordar todo el grupo; rotar es obligatorio.

### 2.2 Superadmin sembrado — Alta [Mío]
`data.sql` crea `admin@plancito.com` con rol `SUPERADMIN` y deja la contraseña escrita en un comentario. Lo corre cada arranque. Si esa cuenta existe en Pampero con esa clave, cualquiera que lea el repo entra como superadmin. Cambiá la clave desde "Perfil → cambiar contraseña" en Pampero y sacá el comentario del archivo (o pasá el hash a una variable). Para la demo la cuenta puede seguir existiendo.

### 2.3 Imágenes de eventos privados sin protección — Alta [Mío]
`WebAuthConfig` deja `/image/**` en `permitAll` y `ImageController` sirve cualquier id sin mirar a qué evento pertenece. Los ids son secuenciales, así que las fotos de un evento privado se pueden bajar sin login probando números. El detalle del evento sí devuelve 404 a quien no puede verlo (`EventAccessChecker`), por eso es una inconsistencia real. **Confirmado en la app real:** con un evento privado con foto, `GET /image/{id}` devuelve 200 y los bytes a un anónimo y a un usuario no invitado, mientras que el detalle del mismo evento da 404.
Arreglo mínimo: `ImageService`/DAO que diga si la imagen pertenece a un evento privado y, en ese caso, exigir `canView`. Si no llegás: documentar el límite y explicarlo en la defensa.

### 2.4 Otros puntos de seguridad
- **[Mío · Baja]** `POST /eventos/{id}/reportar` y `POST /eventos/{id}/favorito/quitar` no llaman a `canView`. En `reportar` hay un comentario que explica que "no revela nada que el 404 no revele igual", pero no es del todo cierto: para un evento privado ajeno la respuesta es una redirección con mensaje y para un id inexistente es un 404, y esa diferencia confirma que el evento existe. Es chico, pero si lo corregís son dos líneas (`rsvp`, `addFavorite` y `ask` sí llaman a `canView`).
- **[Mío · Media · confirmado]** Un ADMIN común puede suspender a otro admin o al superadmin: `AdminController.suspender` solo bloquea suspenderse a uno mismo. `promover`/`revocar` sí están restringidos a SUPERADMIN en `WebAuthConfig` (probé: un admin común recibe 403 al promover, pero logró suspender a `admin@plancito.com`). Arreglo: en `suspender`, rechazar si el objetivo tiene rol ADMIN o SUPERADMIN y quien actúa no es SUPERADMIN.
- **[Cátedra-OK]** CSRF deshabilitado y logout por GET: Sotuyo dijo "por ahora" en clase 4. Conviene dejarlo escrito en el README como decisión.
- **[Mío · Baja]** `LocaleController` cambia un dato en la base (`updateLocale`) con un GET; mejor POST.
- **[Mío · Media · confirmado]** Un usuario suspendido **con la sesión abierta sigue usando la app**: probé que después de suspenderlo pudo anotarse a un evento, publicar uno y hacer preguntas (solo el login nuevo lo frena, con `?suspended`). Como la suspensión es la herramienta de moderación, conviene que corte de inmediato. Opciones simples: que `ErrorAdvice.currentUserFromPrincipal` (que ya carga el usuario en cada request) invalide la sesión y redirija a `/login?suspended` si `user.isSuspended()`; o un `SessionRegistry` para expirar las sesiones al suspender. La primera son ~6 líneas.
- **[Mío · Media · confirmado] Open redirect:** `SafeRedirect.isSafe` rechaza `//` y `://`, pero `/\evil.com` pasa (empieza con `/`, no con `//`, y no contiene `://`), y los navegadores tratan `\` como `/`. Probé `POST /login?redirect=/\evil.com` y el servidor responde `302 Location: /\evil.com`. Afecta al login y a `?returnTo=` de favoritos. Arreglo: rechazar también `\` y caracteres de control (`path.indexOf('\\') >= 0`, o parsear con `URI` y exigir que no tenga host), o directamente comparar contra una lista de prefijos propios.

**Bien resuelto:** redirecciones validadas con `SafeRedirect` (salvo el caso de arriba), el reset de contraseña no revela si el email existe, el escape de salida es consistente (`c:out` y `htmlEscape="true"` en la bandeja) y todos los mappings tienen método HTTP salvo las dos rutas de error.

### 2.5 Subida de imágenes: XSS almacenado por SVG — Alta [Mío]
`ImageServiceImpl.create` solo verifica que el `Content-Type` **que manda el cliente** empiece con `image/`, y `ImageController` lo devuelve tal cual, en línea y desde el mismo origen que la app. Probé subir al publicar un evento: un `.svg` con `<script>` (queda guardado como `image/svg+xml`; abrir `/image/{id}` ejecuta el script en el dominio de la app), un archivo de texto con `Content-Type: image/png` (se guarda como "imagen") y un PNG rotulado como GIF (se guarda). Los límites de tamaño (6 MB rechazado) y de cantidad (9 rechazado) sí funcionan.
Cualquier usuario registrado puede publicar un evento y mandarle el link `/image/N` a un admin. Con CSRF deshabilitado, el script puede hacer cualquier acción como ese admin.
Arreglo: lista permitida (`image/png`, `image/jpeg`, `image/gif`, `image/webp`), validar que los bytes sean realmente una imagen (`ImageIO.read(...) != null` o los primeros bytes mágicos) y en `ImageController` agregar `X-Content-Type-Options: nosniff` (ya lo agrega Spring Security) más `Content-Security-Policy: default-src 'none'; sandbox` en la respuesta. Nunca aceptar SVG.

### 2.6 Título del evento sin escapar en 3 JSP (self-XSS) — Baja [Mío]
`events/inscriptos.jsp:22`, `auth/inscriptos.jsp:26` y `auth/invitar.jsp:26` pasan `${event.title}` como argumento de `<spring:message>` sin `htmlEscape`. Probé con un título `<script>…`: se inyecta en esas tres pantallas. Solo las ve el creador del evento (a los demás les da 404), así que hoy es XSS contra uno mismo, pero es una línea por archivo. `bandeja.jsp` ya lo hace bien (`htmlEscape="true"`): copiá ese patrón. Probé además nombres de usuario, descripciones, direcciones, preguntas, respuestas, reseñas y motivos de reporte con payloads: en todo el resto de las pantallas se escapan bien, en las dos versiones de idioma.

### 2.7 Configuración de mails — Media [Mío]
- `mail.properties` trae `app.base-url=http://localhost:8080` y los 138 links de los mails que capturé apuntan ahí. En Pampero hay que cambiarlo (contexto de la app incluido) o todos los links de los mails (confirmar cuenta, reset de contraseña, "ver mi evento") quedan rotos. Idealmente que venga del entorno, igual que las credenciales (2.1).
- No hay timeouts de SMTP (el log dice `timeout -1`). Probé apagar el servidor de mail: la app se degrada bien (las acciones terminan, el error queda en el log, los mails salen después del commit en otro hilo). Pero si el SMTP en vez de rechazar la conexión **se cuelga**, ese hilo queda bloqueado para siempre. Agregar `mail.smtp.connectiontimeout`, `mail.smtp.timeout` y `mail.smtp.writetimeout` (por ejemplo 5000–10000 ms).

---

## 3. Estructura y capas

### 3.1 Cumple lo que enseñó Sotuyo
- **Jerarquía de capas** sin saltos: ningún `import` del webapp hacia `persistence` ni hacia clases `*Impl` (clase 2 y 3: "responsabilidades y atribuciones de cada capa").
- **Contratos vs implementaciones** con scopes: `services` ve `persistence` en `runtime`, `webapp` ve `services` y `persistence` en `runtime` (clase 3).
- **Inyección por constructor** y ningún `new` de colaboradores. Las únicas excepciones son `@Autowired` en campos de `WebAuthConfig` (3) y `CategoriasValidator` (1) **[Mío · Baja]**: en el validador es habitual, en `WebAuthConfig` se puede pasar a constructor.
- **`Optional` + `EventNotFoundException` + handler global** para el 404 (clase 3, ~l.537–551, y clase 4, ~l.265–275).
- **Validación declarativa** con `@Valid` + `BindingResult` y formularios con anotaciones (clase 3, ~l.1060–1090: "el 100% de la validación se hace a través de anotaciones").
- **Transacciones:** todos los métodos de service tienen `@Transactional`, con `readOnly = true` en las lecturas (clase 4, ~l.716–740). Sin anotación quedan `UserDetailsServiceImpl.loadUserByUsername` (conviene `readOnly`), las clases de mail/notificaciones (`@Async`) y el scheduler, lo cual es razonable.
- **`schema.sql` en `src/main/resources`** [Cátedra-OK] (clase 4). **`web.xml` con `ContextLoaderListener` y `DispatcherServlet` apuntando a `WebConfig`** [Cátedra-OK] (clase 2 ~l.548–600 y PDF clase 21): se carga dos veces a propósito; no lo toques.

### 3.2 Se aparta de lo que enseñó Sotuyo

**Reglas de negocio en el controller** (clase 2/3: el controller solo orquesta; las reglas viven en el modelo/servicios). Es el desvío más importante en estructura:
1. `EventAccessChecker.canView` está en el paquete `controller` pero es una regla de visibilidad de eventos privados. La misma regla se aplica además en SQL (`search` y `findFeatured` filtran `PUBLIC`) y `canView` se invoca a mano en 3 controllers (4 lugares), más los `isPrivate()` sueltos de `EventController` y `ProfileController`. → mover a service (`EventService.canView(event, userId)`).
2. `AdminController`: el chequeo "no podés suspenderte/revocarte a vos mismo" está en el controller; el service no lo sabe.
3. `ProfileController.misEventos` calcula en el controller los confirmados por evento (`findByEventId(...).stream().filter(CONFIRMED).count()` por cada evento, N+1) y `cancelarInscripcion` decide si "se canceló" comparando contra dos valores del enum. → el service debería devolver el dato listo.
4. `ProfileController.misInscripciones` parte "próximas / pasadas" y busca cada evento uno por uno (N+1).
5. `LoginController.autoLogin` arma el `SecurityContext` a mano dentro del controller.
6. La propiedad "este evento es de este usuario" aparece 19 veces como `Long.valueOf(x).equals(event.getCreatorId())` en 8 archivos (services y controllers). → un método `Event.isCreatedBy(long userId)` en el modelo (MVC: las reglas del dominio viven en el modelo).

**Pom:** `jstl:1.2` y `javax.servlet-api:4.0.1` tienen versión escrita en `webapp/pom.xml`; el resto está en `dependencyManagement` del padre (clase 2/3: versiones en el padre, hijos sin versión). `services/pom.xml` y `webapp/pom.xml` además conservan el `pluginManagement` completo del arquetipo y el `FIXME change it to the project's website` (dos veces).

**Formularios:** Sotuyo mostró el taglib `form:` de Spring que genera el HTML y los errores por campo (clase 3, ~l.1081). El repo no usa ningún `form:`: usa el tag propio `paw:input` y cada controller copia los errores a un `Map<String,String>` (ver 6.3). No es un error —funciona y no lo exigió—, pero es la fuente de la duplicación y del problema de "un solo mensaje por campo" que ya figuraba como #3b en tu triage.

### 3.3 Observaciones de estructura [Mío]
- **Servicios que reciben y devuelven cosas de la capa web:** `ImageServiceImpl` tira `IllegalArgumentException("event.publish.imageTooLarge")` (una clave de i18n) y el controller hace `message(e.getMessage())`. Si cualquier otra `IllegalArgumentException` llega ahí (por ejemplo la de `EventServiceImpl.create`, que es una frase, no una clave), `getMessage` tira `NoSuchMessageException` y termina en 500. Mejor un tipo de excepción propio o un resultado (`ImageResult`).
- **Dos formas de decir "no existe":** `RegistrationServiceImpl` (`register`, `isFull`, `isFullFor`, `availableSpots`), `ModerationServiceImpl.report` y `UserServiceImpl.changePassword` tiran `IllegalArgumentException`; el resto devuelve `NOT_FOUND` / `NOT_ALLOWED` / `Optional`. Elegí una. Sotuyo habló de "estado excepcional" vs. regla de negocio (clase 4, ~l.785–800); lo que se espera aquí (el evento fue borrado entre dos clics) es más un resultado que una excepción.
- **Tres maneras de tratar la misma carrera** (insert duplicado): `ReviewServiceImpl.create` atrapa `DataIntegrityViolationException` y devuelve `ALREADY_REVIEWED`; `EventRegistrationController` atrapa lo mismo y reintenta `register`; `EventServiceImpl.create` lo traduce a `IllegalArgumentException`. Sotuyo dijo que las reglas de negocio se validan **antes** del insert (clase 4, ~l.799), no se deshacen después.
- **`register()` chequea antes de lockear:** busca `existing`, *después* toma `findByIdForUpdate`, y no vuelve a chequear. Dos clics simultáneos pasan el primer chequeo y el segundo choca con la unique constraint; de ahí sale el "retry" del controller. `cancel`, `approve`, `reject` y `revoke` sí lockean y recién después releen. Arreglo: tomar el lock primero, chequear después, y borrar `registerRetryingOnConflict` del controller.
- **`EventService.cancel` no lockea la fila del evento** (`findById`), mientras que `update` y `register` sí (`findByIdForUpdate`). **[Baja]** Un `register` simultáneo podría quedar confirmado en un evento recién cancelado sin que le avisen. Es una ventana mínima; lo dejaría anotado.
- **`@Transactional(propagation = REQUIRES_NEW)`** en `UserServiceImpl.register` y `findOrCreate`: no encuentro el motivo en el código (no hay transacción exterior en esas rutas). Con `SimpleDriverDataSource` cada transacción abre una conexión nueva. Preparate una respuesta, porque es el tipo de cosa que preguntan.
- **Identidad doble en el esquema:** `event.creator_email`+`creator_id`, `registration.email`+`user_id`, `event_invitation.invited_by`+`invited_by_user_id`+`user_id`, `inbox_message.recipient_email`+`user_id`. Las columnas viejas siguen `NOT NULL`, se siguen escribiendo y los services siguen mandando mails e inbox por email (`inboxService.add(event.getCreatorEmail(), …)` hace además una consulta de usuario por cada alta). Es una migración a medias. No la terminaría antes del 05/10, pero sí explicaría el plan.
- **`postgres-migrations.sql` hoy re-agrega y borra columnas en cada arranque** (`event.category_id`, `event.image_id`: `ADD COLUMN IF NOT EXISTS` → `INSERT … SELECT` → `DROP COLUMN IF EXISTS`) y recorre varias tablas con `UPDATE … LOWER(email)`. Es idempotente, pero toma locks en `event` cada vez que levanta la app. Cuando Pampero y las bases locales del grupo ya hayan corrido estas migraciones, esos bloques se pueden borrar. Sotuyo recomendó Flyway (clase 4, opcional) justamente para esto.
- **`SimpleDriverDataSource` sin pool** en producción: abre una conexión por cada transacción. Anda para la demo; ¿tenés un pool pensado? (Sotuyo usó esta clase en clase, no pidió pool.)
- **`ErrorAdvice.currentUser`** hace `findByEmail` en cada request y `UnreadInboxInterceptor` repite la misma consulta. El interceptor puede leer `currentUser` del modelo.
- **`services-contracts`** depende de `spring-security-core` solo por `PasswordNotSetException extends AuthenticationException`. El contrato queda atado a Spring Security.

---

## 4. Código muerto (necesito tu OK para borrar)

Verificado por búsqueda con receptor tipado y luego con `grep` sobre `main` (JSP, tags y JS incluidos).

**Métodos de contratos sin ningún llamador en producción** (los tests de persistence que existen para algunos se borrarían junto):

| Método | Dónde | Nota |
|---|---|---|
| `EventService.findAll` / `EventService.findByCategory` | service + `EventDao` + `EventJdbcDao` | nadie los llama; además sin paginar |
| `CategoryDao.findById` | DAO + impl | |
| `EventInvitationDao.deleteByEventAndEmail` | DAO + impl | |
| `InboxMessageDao.findByRecipient`, `countByRecipient`, `countUnread`, `markAllRead` | DAO + impl | versión por email; reemplazada por las `…UserId` |
| `RegistrationDao.findWaitlistedByEvent` | DAO + impl | |
| `UserDao.findRoles` | DAO + impl | los roles ya vienen en el extractor |
| `UserService.findOrCreate` | service + impl | solo se lo nombra en comentarios; tiene `REQUIRES_NEW` |

**Cadena "email del perfil" que ya no hace nada:** el email se muestra deshabilitado y el controller siempre pasa el del usuario logueado. Entonces `ProfileUpdateResult.EMAIL_TAKEN`, el chequeo `existing.getId() != userId`, el parámetro `email` de `UserService.updateProfile`/`UserDao.updateProfile`, `ProfileForm.email` y el `rejectValue("email","email.taken",…)` de `ProfileController` son inalcanzables. Simplificar a `updateName(userId, name)`.

**Configuración y archivos:**
- `registry.addResourceHandler("/images/**")` en `WebConfig` y `"/images/**"` en `WebAuthConfig`: apuntan a una carpeta `/images/` que no existe.
- `webapp/WEB-INF/tags/input.tag.bak` y `webapp/css/style.css.bak`: **están trackeados en git** (lo confirmé con `git ls-files`). Borrarlos con `git rm`.
- `Claude outputs/` en la raíz: aparece como carpeta sin trackear en `git status`. No es de producción; borrala o agregala al `.gitignore` antes de commitear.
- Clase CSS `.filter-bar`: la única de 275 que no se usa en ningún JSP/tag/JS.
- `services/pom.xml` y `webapp/pom.xml`: bloque `pluginManagement` del arquetipo y los dos `FIXME`.
- `_to_delete/revision_src.tgz`: lo dejé en tu carpeta (ignorada por git) al armar la copia; borralo cuando quieras.

**Claves de i18n sin uso** (15 por archivo `messages*.properties`; `messages.properties` es idéntico a `messages_es.properties`):
`PasswordMismatch.userForm/resetPasswordForm/setPasswordForm.confirmarPassword` (3), `Future.eventForm.fechaEvento` y `TooFarInFuture.eventForm.fechaEvento` (quedaron de cuando la fecha pasada se validaba con `rejectValue`; hoy lo hace `ValidEventDate`), `auth.events.pendingBadge`, `auth.profile.dataHeading`, `auth.register.emailTaken`, `barrio.invalido`, `categoria.invalida`, `categoria.vacia`, `event.detail.maxCapacity`, `event.list.neighborhoodAll`, y las tres de `profileForm.email` (`NotBlank`, `Email`, `Size`).
(No marco como muertas las `NotBlank.userForm.nombre`, `Size.…`, etc.: Spring las resuelve por convención de nombre aunque no aparezcan escritas en el código.)

**Datos que se leen de la base y nadie usa** (baja prioridad, son parte del modelo): `Event.getCancelledAt/getRemovedReason/getFeaturedAt`, `EventQuestion.getAnsweredAt`, `EventReport.getResolvedByUserId/getResolvedAt`, `Image.getUploadedAt`, `InboxMessage.getReadAt`, `VerificationToken.setPurpose/setExpiresAt`.

### 4.1 Restos del sistema viejo de lista de espera

El 25/09 FMerlo01 (commit `f8116b6`, "waitlist ya no consume cupo") borró el sistema anterior —`WaitlistService`/`WaitlistServiceImpl`, `WaitlistScheduler`, `WaitlistController`, `WaitlistInvitation` (+DAO), `InvitationStatus`, la pantalla `waitlist/invitacion.jsp` y el flujo "invitación con token que vence en 24 hs"— y lo reemplazó por `promoteFromWaitlist`: quien está en espera pasa solo a confirmado (o a pendiente) cuando se libera un lugar, y ni la espera ni los pendientes ocupan cupo. Ese commit dejó los restos siguientes, ninguno rompe nada hoy pero varios se ven en la defensa:

| Resto | Dónde | Qué hacer |
|---|---|---|
| **Mensaje falso al usuario:** `cupo.muyBajo` dice que el cupo cuenta "invitaciones de la lista de espera sin contestar y pedidos pendientes de aprobación". Hoy `occupiedSpots` es solo confirmados + invitaciones a evento privado sin usar. | `messages.properties`, `_es`, `_en` (línea 215) | Reescribir el texto. Es lo único de esta lista que el usuario puede leer. |
| Tres tipos de mensaje de bandeja que nada genera: `WAITLIST_SPOT_OFFERED`, `WAITLIST_ACCEPTED`, `WAITLIST_DECLINED` | `InboxMessageType`, 3 claves `inbox.type.*` ×3 archivos, y los `or message.type == 'WAITLIST_ACCEPTED'…` de `bandeja.jsp` (líneas 40–41) | Quitar. **Ojo:** `InboxMessageJdbcDao` usa `InboxMessageType.valueOf(...)`; si en Pampero quedó alguna fila vieja con esos tipos, al borrar la constante la bandeja de esa persona tira 500. Primero un `DELETE FROM inbox_message WHERE type IN (…)` en `postgres-migrations.sql`. |
| Tabla `waitlist_invitation` huérfana en las bases viejas (Pampero) | comentario en `schema.sql` líneas 142–143 | `DROP TABLE IF EXISTS waitlist_invitation;` en `postgres-migrations.sql` y sacar el comentario. |
| Rama `rsvp == 'lleno'` en `detail.jsp` (línea 168) y clave `event.rsvp.full` ("Ya no se usa…, se deja por si queda alguna URL vieja") | `detail.jsp`, 3 `.properties` | Nada setea ese valor (`rsvpParam` no lo devuelve; además es un flash attribute, no un parámetro de URL). Quitar las dos. |
| `RegistrationDao.findWaitlistedByEvent` | DAO + impl | Ya estaba en la lista de la sección 4. |
| Comentarios que citan clases que ya no existen: "igual que `WaitlistScheduler`" (×3) | `EventReminderScheduler` | Reescribir. |
| Comentario "Ya no hace falta atrapar `IllegalStateException`…" | `EventRegistrationController` (líneas 73–74) | Borrar: describe un cambio, no el código actual. |
| Mención a `InvitationStatus` (clase borrada) | comentarios de `RegistrationStatus` y `postgres-migrations.sql` | Reescribir. |

Lo que sí quedó vivo y consistente de esa lógica nueva: `findNextWaitlisted`, `countPending`, `moveRemainingPendingToWaitlist`, las plantillas `waitlist-joined`, `waitlist-new-entry` y `waitlist-promoted`, y el índice `idx_registration_waitlist`. Esos se usan.

Mismo patrón de otros borrados antiguos (`Inscripcion*`, `HelloWorld`, `DemoController`, `RegistrationForm`): revisé y no quedaron referencias.

---

## 5. Inconsistencias

- **i18n de validadores custom [Mío · Media]:** `ValidBarrio`, `ValidEventDate`, `ValidCategorias` y `ValidImageCount` no tienen clave en los `messages*.properties`; Spring cae al texto en castellano escrito en el validador, así que en inglés esos errores salen en castellano. Además `InviteForm.email` no tiene claves, así que el error del email en la pantalla de invitar sale en castellano aunque la UI esté en inglés. Para arreglarlo basta agregar claves del tipo `ValidEventDate.eventForm.fechaEvento` (y variantes si querés separar "pasada" de "muy lejana").
- **Mensajes con y sin tilde:** `"El titulo es obligatorio"` / `"La contraseña es obligatoria"` / `"Elegí un barrio"`. Como los `message=` son solo el fallback, no afecta lo que ve el usuario cuando la clave existe, pero queda raro al leerlo. Idem `rejectValue("cupoMaximo","cupo.muyBajo","El cupo maximo…")`.
- **Nombres:** mezcla español/inglés en el mismo archivo (`EventForm.titulo` vs. `Event.title`; `CADA_QUINCE_MINUTOS` junto a `ONE_DAY_MILLIS`; URLs y métodos del `ProfileController` en español). No es un error, pero elegí una convención y mencionala.
- **Dos páginas con la misma URL "inscriptos":** `/eventos/{id}/inscriptos` (confirmados, con "Revocar") y `/perfil/eventos/{id}/inscriptos` (pendientes de aprobación). El redirect de `revokeRegistration` a `/eventos/...` es intencional (hay un comentario), pero para quien lea el código es fácil de confundir con un bug.
- **`availableSpots` devuelve `Integer` con `null` = "sin límite"** mientras el resto del proyecto usa `Optional` para ausencias (clase 3).
- **`EventForm` depende del controller** (`EventController.MAX_IMAGES`): el formulario no debería importar el controller.
- **Misma regla de fecha en tres lugares:** el patrón `yyyy-MM-dd'T'HH:mm` aparece en `EventForm` (dos veces) y `EventDateValidator`, y el regex se repite entre `@Pattern` de `EventForm` y `SHAPE_REGEX` del validador.
- **Reglas de contraseña duplicadas** (`@Size(min=8,max=100)` + mensaje + "Tenés que confirmar…") en `UserForm`, `ChangePasswordForm`, `SetPasswordForm`, `ResetPasswordForm`.
- **`ErrorAdvice` [Mío · Media]** con `@ExceptionHandler(Exception.class)`: lo probé con un controller de prueba y un `GET` a una ruta que solo acepta `POST` da **500** (`HttpRequestMethodNotSupportedException`), y un parámetro obligatorio faltante da **500** (`MissingServletRequestParameterException`), con `LOGGER.error` y stack trace en cada caso. Corresponderían 405 y 400. Arreglo: dos handlers más específicos (o que `unexpectedError` re-lance las `ServletException` conocidas). Sotuyo mostró el handler global para el 404 (clase 4); no dijo nada sobre el resto de los códigos.
- **`MultipartException`** siempre redirige a `/eventos/publicar?imageError=true`, incluso si el usuario estaba editando.
- **Orden de entrega:** imágenes se suben antes de crear el evento y fuera de la transacción del evento (`EventController.uploadImages`). Si falla el `create` (o la tercera imagen), quedan filas de `image` huérfanas. Ya estaba en tu triage del 08/09; sigue igual.
- **Misma ruta, dos significados de `{id}` [Mío · Baja]:** `POST /perfil/inscripciones/{id}/cancelar` recibe el id de la **inscripción**, pero `POST /perfil/inscripciones/{id}/resena` recibe el id del **evento** (hay un comentario en el controller que lo aclara, y `inscripciones.jsp` ya manda `event.id`). Mover la reseña a `/eventos/{id}/resena` evita la trampa.
- **Los contadores del sidebar** (`registrationsCount`, `favoritesCount`, `eventsCount`, `invitationsCount`) cargan listas completas para llamar `.size()`; deberían ser `COUNT(*)`.

---

## 6. Duplicación

### 6.1 `NotificationServiceImpl` — 26 métodos con el mismo esqueleto [Mío · Media, refactor]
Cada `notifyXxx` repite: `try { locale = localeForUserId(...); variables = eventVariables(...); variables.put("personName", …); mailService.send(email, "plantilla", "clave.subject", args, variables, locale); } catch (RuntimeException e) { LOGGER.error(…) }`. Lo que cambia entre métodos es solo (destinatario, plantilla, clave de asunto, variables extra, texto del log). Un helper `send(recipient, template, subjectKey, args, extraVars)` con el `try/catch` adentro deja cada método en 3–5 líneas y achica el archivo (~700 líneas hoy) a una fracción. Los tests de mail quedaron afuera del refactor de tests que hablamos, así que no hay red de seguridad: **no lo haría antes de la entrega**, toca los 26 mails.

### 6.2 `UserJdbcDao` — el mapeo de usuario con roles está tres veces
`USER_WITH_ROLES_EXTRACTOR`, `findAll` y `findByIds` pivotean las filas igual (`new User()`, setear campos, sumar rol). Sotuyo pidió un `RowMapper`/extractor `static final` reutilizado ("no nos repetimos", clase 3); acá existe, pero se copió en vez de reutilizar. → extraer un único extractor que devuelva `List<User>` y que `findById`/`findByEmail` usen tomando el primero.

### 6.3 Errores de formulario → mapa
`messageFor(FieldError)` está copiada en 5 controllers y el bucle `for (FieldError …) fieldErrors.put(…)` aparece en 7 lugares (`ProfileController`, `EventController` ×2, `LoginController`, `RegisterController`, `PasswordResetController` ×2). Una clase `FormErrors.toMap(BindingResult, MessageSource)` lo resuelve; o usar `form:errors`.

### 6.4 `ProfileController`
- El bloque de cuatro contadores se repite en 13 `ModelAndView`. Un solo helper (`addSidebar(mav, user)`) o un `@ModelAttribute` limitado al controller. Sotuyo mostró `@ModelAttribute` en un controller global para el usuario actual (clase 3, ~l.543); esto sería el mismo mecanismo (extrapolación mía).
- Líneas duplicadas por copy-paste: `invitationsCount` está dos veces seguidas en `updateProfile` (líneas 164–165) y en `changePassword` (195–196). Inofensivo, pero se ve en una revisión.
- `perfil()`, `updateProfile()` y `changePassword()` arman el mismo modelo en tres lugares.

### 6.5 Otros
- Normalización de emails `.trim().toLowerCase()` en 10 lugares de 4 services (y `LOWER(...)` en el SQL). Una función `Emails.normalize()` (y `Locale.ROOT`, porque `toLowerCase()` sin locale falla en turco).
- `EventInvitationServiceImpl.invite` recalcula la ocupación (`countConfirmed + countOutstanding`) igual que `RegistrationServiceImpl.occupied`. Usar `RegistrationService`.
- Cada acción de dominio hace "mail + inbox" en dos llamadas seguidas (~25 pares). Sería más limpio un único punto de notificación por evento; no tocar ahora.
- `LoginController` / `RegisterController`: `"redirect:" + (SafeRedirect.isSafe(redirect) ? redirect : "/eventos")` ×5 y el sufijo `"&redirect=" + URLEncoder…` ×3.
- Los 22 JSP completos repiten el mismo `<head>` (favicon + `style.css` con `${pageContext.request.contextPath}` ×46). Un tag `paw:layout` y `c:url` (Sotuyo usó `c:url`, clase 3) lo resuelve; ya figuraba como #21 en tu triage con 3 casos y hoy son más.
- i18n: `en` y `es` tienen las mismas claves (518); `messages.properties` es una copia byte a byte de `messages_es.properties` (599 líneas). Sirve como fallback, pero cada cambio en castellano hay que hacerlo en dos archivos. Es una trampa de mantenimiento; no la tocaría ahora.

---

## 7. Lo que está bien (para tu defensa)
- Capas limpias y contratos/implementaciones con scopes correctos; cero `import` cruzados.
- 644 tests verdes, services con Mockito y persistence con HSQLDB + `JdbcTestUtils`, siguiendo lo que dijo Sotuyo en clase 4.
- Resultados enumerados (`RegistrationResult`, `ApprovalResult`, …) en vez de excepciones para casos de negocio esperados.
- Bloqueo pesimista (`SELECT … FOR UPDATE`) en cupos y lista de espera.
- Open-redirect controlado (salvo el caso `/\` de 2.4), reset de contraseña sin filtrar existencia de cuentas, escape de salida consistente (salvo los 3 JSP de 2.6).
- Probado en la app real (sección 10): 12 anotaciones simultáneas con cupo 3 dan exactamente 3 confirmados y 9 en espera; seis clics simultáneos del mismo usuario dejan una sola inscripción; bajas simultáneas promueven bien desde la lista de espera; los mails salen una vez por acción, con el contenido de usuario escapado; si el SMTP está caído la app sigue andando; los recordatorios de 24 hs son idempotentes y llegan solo a quien corresponde.
- CSS limpio (1 clase sin uso en 275) y los 14 archivos JS están referenciados por alguna vista.
- i18n completa (mismas 518 claves en es/en) y mails con plantillas Thymeleaf separadas.

## 8. Preguntas probables en la defensa (por los hallazgos de arriba)
1. ¿Por qué `EventAccessChecker` está en el controller si es una regla de negocio?
2. ¿Qué pasa con dos clics simultáneos en "Quiero ir"? ¿Por qué el controller reintenta?
3. ¿Por qué `REQUIRES_NEW` en `register`?
4. ¿Qué diferencia hay entre `creator_email` y `creator_id`? ¿Por qué están los dos?
5. ¿Dónde están las claves de producción? ¿Quién puede entrar como superadmin?
6. ¿Por qué `/image/{id}` es público?
7. ¿Qué pasa si el servidor de mail está caído cuando alguien se registra? (Se loguea y la acción termina; falta el timeout, 2.7.)
8. ¿Cómo se actualiza el esquema en producción cuando agregan una columna? (Sotuyo: `ALTER` al final de `schema.sql` o Flyway.)

## 9. Plan sugerido (3 días)
- **Hoy, primero:** el bug de la sección 0 (`binding = false` + test de regresión + revisar datos de prueba desde el 01/10). Después: rotar credenciales y sacarlas del repo (2.1), cambiar la clave del superadmin (2.2), borrar el código muerto confirmado (sección 4) con tu OK, líneas duplicadas y `.bak`.
- **Mañana:** `register()` (lock primero, sin retry en el controller), handlers 405/400 en `ErrorAdvice`, claves i18n de validadores, `Event.isCreatedBy(...)`, protección de `/image/{id}` para eventos privados, validación real del tipo de imagen (2.5), cortar sesiones de suspendidos (2.4), `SafeRedirect` con `\`, `htmlEscape` en los 3 JSP (2.6), `app.base-url` y timeouts de mail (2.7).
- **Antes de la demo (sábado):** correr todo en Pampero con datos reales, verificar el registro simultáneo con dos pestañas y que la app levanta con las credenciales nuevas desde el entorno.
- **Después de la entrega:** `NotificationServiceImpl`, `UserJdbcDao`, `ProfileController`, `paw:layout`, migración a `user_id` y Flyway.

---

## 10. Flujos probados en la app real

Entorno: Tomcat 9 con JSP reales sobre una copia de tu commit `3fa5390` **con el arreglo de la sección 0 aplicado** (sin él, los flujos de cupo/edición/cancelación no se pueden ni probar), PostgreSQL 16, servidor SMTP falso que guarda los mails. Datos de prueba propios, nada de tu base ni de internet.

**Lo que funciona bien (probado):**
- Cuentas: registro con validaciones (clave corta, claves distintas, email duplicado, email en mayúsculas), login sin verificar, reenvío de confirmación, token de verificación de un solo uso, token inválido, cambio de contraseña (con la actual incorrecta y correcta; la vieja deja de servir), reset de contraseña (token de un solo uso, email inexistente no revela nada), perfil.
- Cupo y lista de espera: confirmados hasta el cupo, el resto a espera en orden; la baja de un confirmado promueve al primero de la espera; la baja de alguien en espera no cambia nada; reanotarse tras bajarse; el creador no se anota a su propio evento; subir el cupo a ilimitado promueve a toda la espera; bajar el cupo por debajo de los ocupados se rechaza con mensaje (cuyo texto está desactualizado, 4.1).
- Aprobación: pedidos pendientes, aceptar hasta llenar (los que quedan pasan a espera), aceptar de más / aceptar dos veces / rechazar a alguien que no está pendiente no rompen nada, revocar libera el lugar y vuelve a pendiente a quien esperaba, un rechazado no puede reanotarse, un tercero que intenta aceptar o rechazar un pedido pendiente no logra nada (el pedido sigue pendiente) y no puede ver la lista.
- Eventos privados: no aparecen en listados ni en la home; el detalle, `quiero-ir`, preguntas e inscriptos dan 404 al no invitado; las invitaciones respetan el cupo (confirmados + invitaciones sin usar, sin contar dos veces a quien ya se anotó); invitar a un email sin cuenta, a uno mismo, repetido o inválido responde bien; el invitado ve y se anota; cuenta la invitación como lugar reservado.
- Cancelar evento: avisa por mail y bandeja a los confirmados; cancelar dos veces es inocuo; un tercero recibe 404.
- Reseñas (se rechazan: evento futuro, segunda reseña de la misma persona, quien no se anotó, puntaje 9), preguntas y respuestas (solo el creador responde, una sola vez), reportes (uno por persona, no el propio evento), pedir cambios / descartar / dar de baja con resolución final que no se pisa, destacar y quitar destacado, eliminar pregunta (siempre con permisos de admin; a un usuario común le da 403).
- Concurrencia: 12 `quiero-ir` simultáneos con cupo 3 → 3 confirmados + 9 en espera; 6 clics simultáneos del mismo usuario → una sola fila; 3 bajas simultáneas → se promueven 3 de la espera. Sin 500 ni sobreventa.
- Mails: 140 capturados, las 26 plantillas se usan, ningún placeholder sin reemplazar, contenido de usuario escapado, uno por acción. Recordatorio de 24 hs: llega al asistente confirmado y al organizador, no a la lista de espera ni a eventos cancelados o lejanos, y la segunda corrida no repite.
- Páginas: 846 requests GET (9 roles/estados × 47 URLs × 2 idiomas) sin ningún 500 ni clave de traducción rota. Cargué las pantallas principales en un navegador real con 4 roles: sin errores de JavaScript ni recursos 404. El asistente de publicar (5 pasos) funciona de punta a punta con el navegador (categorías con preguntas, calendario, ubicación, precio, cupo, foto) y el evento queda bien en la base.
- Validaciones de publicar/editar: fecha pasada, fecha a 5 años, formato de fecha, precio negativo/enorme/con coma, cupo negativo/enorme, título vacío o de 151 caracteres, descripción de 1001, sin categoría, categoría inexistente, barrio inválido: todas se rechazan con el formulario de vuelta (200 con errores).

**Lo que encontré** (ya incorporado arriba): sección 0 (binding de `currentUser`), 2.3 (fotos privadas, ahora confirmado), 2.4 (suspendido con sesión abierta, admin suspende superadmin, open redirect con `\`), 2.5 (SVG/archivo arbitrario como imagen), 2.6 (3 JSP sin escapar), 2.7 (base URL y timeouts), y la ruta de reseña de la sección 5. Además confirmé que `GET`/`DELETE` sobre una ruta solo-POST dan 500 (sección 5, `ErrorAdvice`).

**Lo que NO pude cubrir:** interacciones de JavaScript más allá del asistente de publicar (favorito con `fetch`, galería, calendario de filtros, selección múltiple de la lista); los mapas, que cargan Leaflet desde `unpkg.com` y buscan direcciones en Nominatim (necesitan internet: si la red de la demo falla, hay que saber qué se ve; el geocoding falla sin romper la publicación, el comportamiento de Leaflet sin red no lo probé); cómo se ve en celular (CSS/responsive); rendimiento con muchos datos; el despliegue en Pampero (contexto de la app, variables de entorno); cómo se ven los mails en clientes reales (Gmail/Outlook); accesibilidad.
