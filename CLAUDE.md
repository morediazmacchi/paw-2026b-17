# Plancito — contexto para Claude (LEER ANTES DE HACER CUALQUIER COSA)

Proyecto de la cátedra PAW (ITBA), grupo 17: Spring MVC + JSP + JPA/Hibernate + Postgres.
Capas: `model` → `persistence-contracts` → `persistence` → `services-contracts` → `services` → `webapp`; cada una
solo habla con la de abajo (la implementación entra por scope `runtime`, nunca `compile`).

TP1 salió con **4/10**. Este archivo es la fuente única de las reglas que la cátedra marca como error: si vas a
escribir algo que cae en la lista de abajo, pará y resolvelo bien de entrada.

**Marcas de verificación** (para saber cuánto confiar en cada regla):
`[clase N:Lxxx]` = confirmada en la transcripción (ver `RESUMEN-CLASES.md`) ·
`[código]` = confirmada contra este repo · `[TP1]` = viene de la devolución de TP1 vía el `.md` de una compañera, el PDF
original no está a mano (no es citable textual) · `[acordado]` = lo decidió la usuaria.

## 0. Cómo trabajar con la usuaria (lcrosta@itba.edu.ar)

- **Español siempre.** Pronombres neutros (they/them) para cualquier persona: no inferir género.
- **No commitear ni pushear si no lo pide.** Dar los comandos, no ejecutarlos. Atribución de commits:
  `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
- **Nada irreversible sin avisar *antes*, en claro, y esperar el OK:** `DROP DATABASE`, borrar datos, tocar Pampero,
  `git reset --hard`, borrar ramas. (Ya hubo un incidente con un `DROP DATABASE` y se enojó con razón.)
- **Pampero (base de producción de la cátedra) no se toca.** `db.properties` apunta a Pampero por defecto y es config
  local: no se commitea ni se modifica. Nunca correr la app local contra Pampero. No puede haber pérdida de datos.
- Al explicar código para la defensa oral: **paso a paso**, no todo junto.
- Responder con recomendación, no con un menú de opciones; pero **las decisiones de producto/riesgo las toma ella**.
- Si algo del pedido choca con una regla de abajo, **decirlo** antes de hacerlo.
- Después de cada `git pull`: `./mvnw clean install` completo y avisar el resultado antes de seguir. El proyecto tiene
  que compilar completo desde la raíz.
- **Tests: cuando algo falla, mostrar el error; nunca decir "pasó" sin haberlo corrido.**

## 1. Checklist de la cátedra (aplica a TODO código nuevo)

### Persistencia / SQL
- Nunca traer todo a memoria para filtrar/paginar/contar: `WHERE`, `LIMIT/OFFSET`, `COUNT` en SQL. [TP1]
- Nunca N+1. Resolver con `JOIN`, batch o "ids primero, después `IN (...)`". [TP1]
- Escapar `%` y `_` en toda búsqueda `LIKE` con texto de usuario. [TP1]
- No cargar campos pesados (bytes de imagen) en un camino que no los usa; usar **proyecciones** cuando solo hacen
  falta pocas columnas (ej. `ImageDao.findOwnerEventAccess`). [TP1][código]
- Nada de entidades con id de relleno (`0`/`null`) para pisarlo después: el id lo da la base. [TP1]
- Un DAO es dueño de su tabla: no repartir escrituras de una tabla entre varios DAOs. [TP1]
- No mezclar DDL con datos de seed en el mismo script. [TP1]
- Los tests usan el **esquema real** (`schema.sql`), no declaran DDL propio. [TP1][código]
- **Validar antes de escribir, nunca escribir y después deshacer** una regla de negocio. [clase 4:L790-804]
  Si el esquema ya lo garantiza (FK, UNIQUE), dejar fallar el INSERT y traducir la excepción. [TP1]
- **Migraciones (`postgres-migrations.sql`):** archivo append-only, cada sentencia **idempotente** (corre en cada
  arranque), cuidando no perder datos. [clase 4:L692-714] ⚠️ Un `ADD COLUMN`+`DROP COLUMN` incondicional por arranque
  llevó `event` a 1574 columnas borradas (límite 1600 de Postgres) y tumbó Jetty. Hoy ese archivo solo tiene backfills
  `UPDATE ... WHERE ... IS NULL`; `schema.sql` cubre las bases nuevas. No volver a poner ALTER sin guarda.

### Seguridad / autorización
- **Ownership y "quién puede hacer esto" van en Spring Security** (`@PreAuthorize`/`access(...)`), no como `if` a mano
  en controller/service, **y nunca duplicado** (Security + a mano) de forma redundante. [TP1][clase 4:L960-975]
- **Qué existe hoy** [código]: `EventSecurity` (webapp/security) con `isOwner`, `isOwnerOfPrivate`,
  `isOwnerOfRegistrationEvent`, `isRegistrant`, `canView`, `canRegister`, `isChatMember`, `isConfirmedAttendee`, `canViewImage`;
  `AdminSecurity` (`canSuspend`, `canRevokeAdmin`, `canModerateEvent`, `canResolveReport`). El usuario de la sesión se lee con `PlancitoUserDetails.from(authentication)` y `hasRole(Role.X)`. Se registra con `@Bean` en `WebConfig` (RootConfig solo
  escanea sus paquetes) donde también está `@EnableGlobalMethodSecurity(prePostEnabled, proxyTargetClass)`.
  `AccessDeniedException` → mismo 404 que un evento inexistente (`ErrorAdvice`; `ImageController` tiene su handler
  local que responde 404 sin cuerpo). Solo lee **services**, nunca DAOs.
- **Visibilidad ≠ ownership.** `EventAccessService.canView` decide quién *ve* un evento (acepta `EventAccessData`: lo
  implementan `Event` y la proyección `EventAccessSummary`). `getEditableEvent(id)` solo valida estado (no cancelado).
- El orden de las reglas en `filterChain` importa: gana la primera que matchea; `anyRequest()` siempre última.
  [clase 4:L970-980]
- **Criterio del docente (30/10): la seguridad va primero, aunque cueste una consulta repetida.** Si autorizar a la entrada
  obliga a consultar algo que el handler vuelve a consultar después (ej. la inscripción en el detalle de un evento privado),
  es aceptable. No contorsionar el diseño (ni mezclar autorización con carga de datos) para evitar esa consulta.
- **"El id no existe" nunca devuelve `true`/pasa de largo**: bloquear (404/403) antes. [TP1]
- **Revocar/degradar un rol tiene que afectar la sesión activa**, no solo el próximo login. [TP1] (pendiente, ver §3)
- Dos ids relacionados en una URL → verificar que el segundo pertenezca al primero (`isOwnerOfRegistrationEvent`). [TP1]
- Nunca revelar con una respuesta distinta si un mail/usuario ya existe (registro, recupero). [TP1]
- Escapar texto de usuario en HTML (`<c:out>`): nunca `${...}` crudo en texto visible o atributo (XSS). [TP1]
- Filtro de encoding UTF-8 antes que el de Spring Security en `web.xml`. [TP1]
- Mass assignment: **no hay `@InitBinder`/`setAllowedFields` en ningún controller** [código]. No estaba en TP1 pero es un
  gap real: revisar que ningún form exponga más campos bindeables de los necesarios.
- **CSRF deshabilitado a propósito**: la cátedra lo dejó fuera de alcance, dicho en clase. No es un olvido. [TP1]

### Arquitectura de capas
- Sin lógica de negocio en controllers; el controller arma el modelo y delega. [TP1]
- Una operación lógica = una transacción; nunca partirla en dos services con commits separados. [TP1]
- Escritura → `@Transactional`; lectura → `@Transactional(readOnly = true)`. Ni de más ni de menos. [TP1]
- Procesos masivos (notificar a todos) no corren completos y sincrónicos dentro de la transacción principal. [TP1]
- No instanciar una entidad en el controller y que el service devuelva "otra" nueva: una sola fuente de verdad. [TP1]
- Un módulo depende en `compile` solo de `-contracts`; `webapp→services/persistence` van `runtime`. [código]
  Así un error lógico se vuelve error de compilación. [clase 1:L922-941]
- Un "no encontrado" **no es excepción** en persistencia/service (`Optional`); la excepción HTTP se lanza en la capa web.

### Modelo / tipos
- Dinero = `BigDecimal`. Conjunto cerrado de valores = `enum`, no `String` mágico. [TP1]
- Roles: hoy hay `"ROLE_ADMIN"` etc. como literales sueltos por varias clases [código]. El problema marcado es el mismo
  concepto en varios formatos: cualquier arreglo debe **unificar** (constantes/enum central), no solo convertir.
- Bean Validation (`@Valid`) para todo lo de un form; nunca reimplementar required/longitud/regex a mano. [TP1][clase 3:L769]

### Tests
- **Services: Mockito puro.** Spring/HSQLDB solo si es explícitamente test de integración, **y nombrarlo así**
  (`...IntegrationTest`). [TP1]
- **Persistencia:** Arrange y Assert por **SQL directo** (`jdbcTemplate`), nunca con otro método del mismo DAO bajo prueba.
  HSQLDB en memoria + `@Transactional`/`@Rollback` (nunca `DELETE` manual en `@After`). [TP1][código]
- ❌ **`Mockito.verify()` / `verifyNoMoreInteractions` / `never()` / `times()` / `ArgumentCaptor` para confirmar
  "se llamó a X": PROHIBIDO.** Sotuyo: *"Validar la implementación. Es un error conceptual grave"* [clase 4:L234-252].
  Si un método `void` parece pedir `verify`, **cambiar el tipo de retorno** para poder assertear el efecto (result-enum),
  o assertear el estado observable. Si el efecto no es observable en un test unitario (ej. `markRead`), **no se testea
  ahí**: se cubre en el test de persistencia (SQL directo) o no se testea.
- ❌ **Nunca reflection** para testear un detalle de implementación (setear un campo, invocar un método privado,
  confirmar que hay una anotación). [TP1]
- **Arrange/Act/Assert:** el **Act es UNA línea**; **un test = un escenario**; asserts pocos. El test es aislado y
  repetible. [clase 4:L44-80]
- Se testea el **contrato**, no la implementación; no asertar "la misma instancia que devolvió el mock". [clase 4:L190-196]
- El nombre del test describe lo que verifica; un test de escritura verifica el **estado real (la tabla)**. [TP1]
- ❌ **Tampoco "verify casero" ni nada parecido:** listas/contadores/flags que registran "se llamó a X" o "con este
  argumento", `doAnswer`/`thenAnswer` que guardan lo recibido, stubs que tiran excepción si se los llama, fakes que
  cuentan invocaciones. Todo eso es `verify` con otro nombre y la usuaria lo prohíbe (dos veces corregida).
  Lo que SÍ vale: assertear el **valor de retorno** o el **estado real**; y hacer que el resultado **dependa** de la
  interacción correcta (ej. un stub con `argThat` que solo devuelve datos si el argumento es el correcto, y assertear el
  resultado; o dejar en el dao datos "señuelo" que harían que el resultado fuera distinto si el service los consultara).
- Un test que solo existía para un `verify(never)` **se borra** (no se disfraza). Un test se nombra por lo que prueba.

### Build / repo
- **Toda versión (dependencias y plugins) en el POM padre**, nunca repetida en un hijo. [TP1] (hecho por Sol en
  `9322c6e`; al agregar algo nuevo, declararlo en `dependencyManagement`/`pluginManagement`).
- No versionar nada ajeno a la app (scripts, resúmenes, capturas, config de IDE). **Este `CLAUDE.md` es contexto de
  trabajo: está sin trackear a propósito — no hacer `git add .` a ciegas** (avisar a la usuaria antes de cualquier commit).
- Dependencia nueva entre módulos: respetar el scope (§ Arquitectura).

### i18n
- Nada de texto visible hardcodeado en una JSP (literales, formatos de fecha, sufijos "hs"). Todo por `messages*.properties`
  (`messages.properties` = español base; `_en` = inglés). Mail: `mail-messages.properties` = base; `_es` vacío a propósito.

## 2. Convenciones propias del proyecto (ya aplicadas, mantenerlas)

- **Result-enum** en vez de boolean/excepción cuando el caller distingue varios desenlaces (`RegistrationResult`, ...).
- **DAO:** `em.find()` + setters para mutaciones puntuales (dirty checking). `update()` completo usa whitelist de campos.
- **`findByIdForUpdate()` + chequeo de estado** antes de una transición que pueda chocar con otra concurrente. El lock
  evita la escritura simultánea; el chequeo de estado evita repetir la acción. Orden de locks: evento primero, luego registration.
- `@PreAuthorize` va **sobre el handler del controller**; la regla vive en `EventSecurity`, no en el controller.
- **Paquetes de seguridad en `webapp`:** `webapp.auth` = AUTENTICACION (`UserDetailsServiceImpl`, `PlancitoUserDetails`; como en
  la clase 4:L1028-1070 de Sotuyo, que lo registra con `@ComponentScan` en `WebAuthConfig`); `webapp.security` = AUTORIZACION
  (`EventSecurity`); `webapp.config` = `WebAuthConfig`/`WebConfig`/`RootConfig`.
- Resúmenes de motivos de diseño para la defensa: ver §5.

## 3. Deuda conocida — dónde el código actual NO cumple las reglas de arriba

Registro honesto; no tapar ni "arreglar de pasada" sin avisar. Orden = prioridad sugerida.

1. ~~**Ownership duplicado en services**~~ **Resuelto** (30/10): `EventService.update/cancel`, `EventInvitationService.invite/findByEvent`,
   `RegistrationService.approve/reject/revoke/findPendingByEvent/countPendingByEvent` y `EventQuestionService.answer` ya no reciben
   el id del dueño ni lo comparan; lo decide solo `@PreAuthorize` (`isOwner`/`isOwnerOfPrivate`/`isOwnerOfRegistrationEvent`).
   Los services siguen validando estado (evento cancelado/terminado, inscripción PENDING, pregunta del evento). Se borraron los
   tests de "no es el dueño". Probado en la app: dueña 200; extraño 404 en editar/cancelar/responder/aceptar/rechazar/revocar/invitar.
2. ~~`/perfil/inscripciones/{id}/cancelar`~~ **Resuelto**: `@PreAuthorize("@eventSecurity.isRegistrant(#id, authentication)")`;
   la regla da `false` también si la inscripción no existe (404, opción B: el doble clic muestra 404, aceptado).
   `RegistrationService.cancel(registrationId)` ya no recibe `userId` ni compara el dueño. Probado a mano con 2 cuentas.
3a. **`currentUser` sale del principal, sin consulta** (31/10): `ErrorAdvice.currentUserFromPrincipal` arma un `User` con id, email, nombre y roles de `PlancitoUserDetails` (sin `findByEmail`; sin sesión es `null`). Es un `User` "de sesión": solo trae esos 4 datos. Los 4 `create` de DAO que reciben ese usuario (`RegistrationJpaDao`, `EventQuestionJpaDao`, `EventReportJpaDao`, `ReviewJpaDao`) lo reemplazan por `em.getReference(User.class, id)` antes de persistir (`NotificationService.notifyNewQuestion` recibe `askerName`/`askerEmail` ya resueltos desde `currentUser`: el envío es `@Async` y no debe leer la relación lazy; `register` copia nombre/mail en la fila y las notificaciones solo leen esas copias y el id del usuario).
   **Limitaciones conocidas (aceptadas, no arregladas a propósito):** (1) el **nombre** del principal es el del login: si se edita el perfil se sigue viendo el viejo (form de `/perfil`, copias en inscripciones y bandeja) hasta el próximo login; (2) los **roles** también son los del login: revocar `ROLE_ADMIN` no afecta la sesión activa (ver punto 3). Siguen pendientes los interceptores `UnreadInbox` y `Suspended` (hacen `findByEmail` por request).
3. **Revocar `ROLE_ADMIN` no cierra sesiones** (error marcado en TP1, pendiente). Plan: `HttpSessionEventPublisher`
   (`SecurityWebApplicationInitializer`), `SessionRegistry` + `sessionManagement().maximumSessions(-1)` en `WebAuthConfig`,
   `UserRolesChangedEvent` (services-contracts), `UserServiceImpl` publica tras `grantAdmin/revokeAdmin`,
   `SessionExpiringListener` (`@TransactionalEventListener`) hace `expireNow()`. Probar con dos navegadores.
4. **Visibilidad decidida en los services**: ~~preguntar~~ y ~~favorito~~ **resueltos** (30/10): `@PreAuthorize(canView)` en
   `ask` y `addFavorite`; se sacó el chequeo de `EventQuestionServiceImpl.ask` y de `FavoriteServiceImpl.add`, y se borró
   `EventAccessService.getViewableEvent` (ya no lo usaba nadie). `FavoriteServiceImpl.viewableFavorites` sigue usando `canView`
   a propósito: filtra una lista de resultados, no es una puerta de entrada. ~~`quiero-ir`~~ **resuelto** (30/10):
   `@PreAuthorize("@eventSecurity.canRegister(#id, authentication)")` (= `canView` SIN la excepción de admin: el admin ve pero
   no se anota, salvo que lo hayan invitado a un privado) y `RegistrationServiceImpl.register` ya no decide visibilidad (solo
   usa la invitación para reservar el lugar). Probado con admin invitado (se anota) y sin invitación (404).
   Auditoría de controllers (30/10): ~~chat~~ **resuelto para la pantalla, la consulta y el envío** (`@PreAuthorize(isChatMember)`;
   quien no es miembro ve 404; `event-chat.js` ahora trata 404 igual que 403 en la consulta y en el envío; verificado ejecutando
   el JS real en Node con un DOM de mentira, que falla contra el JS anterior). ~~`chat/unirse`~~ **resuelto**:
   `@PreAuthorize(isConfirmedAttendee)` (lugar CONFIRMADO; el organizador no entra); `EventChatServiceImpl.join` ya no decide el
   permiso (solo busca la inscripción por su id; si no está, `EventNotFoundException`) y se borró `ChatJoinResult.NOT_ALLOWED`
   con sus textos i18n. `chat/salir` no necesita nada (borra tu propia membresía). **Chat completo.** **Responder una pregunta** (31/10): `@PreAuthorize("@eventSecurity.canAnswerQuestion(#questionId, authentication)")`, se decide solo por el id de la pregunta (el `{id}` del evento en la URL es ruteo, no autoriza); lectura acotada `EventQuestionDao.findEventCreatorId` (una columna: `event.creator_id`). `EventQuestionService.answer(questionId, text)` ya no recibe el evento (usa el de la pregunta); se borró `AnswerResult.NOT_ALLOWED` (queda `NOT_FOUND` por si un admin borra la pregunta justo en el medio). (30/10, 2ª pasada) `EventChatServiceImpl.send/findMessages` ya no vuelven a chequear la membresía: se borró `ChatSendResult.NOT_ALLOWED`; `findMessages` devuelve `List` (no `Optional`); `send` lanza `EventNotFoundException` si el evento no existe. Probado en la app: miembro 200/204, no miembro y ex-miembro 404.
   **Admin tipo A resuelto** (30/10): quién suspende / quita el rol a quién ahora es `@PreAuthorize` (`AdminSecurity.canSuspend`,
   `canRevokeAdmin`; no a sí mismo, a otro admin solo un superadmin, id inexistente = denegado). Se borraron
   `UserService.suspendByAdmin`, `InvalidAdminActionException`, su handler en `ErrorAdvice` y el cartel `adminError`;
   `revokeAdmin` ya no recibe `callerId`. En el área de admin un permiso denegado responde **403** (handler local en
   `AdminController` + `errors/403.jsp` nueva; en el resto de la app es 404 a propósito). **Admin tipo B resuelto** (30/10): un admin no aprueba/rechaza ni resuelve reportes (descartar, pedir cambios, dar
   de baja) de SU propio evento ahora es `@PreAuthorize` (`AdminSecurity.canModerateEvent` / `canResolveReport`). Para los reportes
   la regla mira solo de quién es el evento, **no el estado del reporte** (uno ya resuelto por otro admin sigue llegando al
   service y muestra su cartel; id inexistente = 403); usa una lectura acotada nueva, `ModerationService.findReportTarget` →
   `EventReportDao.findTargetById` (proyección `ReportTarget`, 2 columnas). Se borraron los 5 chequeos `OWN_EVENT` de los
   services, `EventApprovalResult.OWN_EVENT`, `ReportResolutionResult.OWN_EVENT` y sus carteles; `EventService.approve/reject` ya
   no reciben `adminUserId`. Las 3 JSP de admin esconden los botones en eventos propios (id leído de
   `<sec:authentication property="principal.id"/>`) y muestran la nota. `ReportResult.OWN_EVENT` (un usuario no reporta su propio
   evento) **no se tocó**. La invariante "un admin no se suspende a sí mismo" sigue garantizada por `canSuspend` (target ≠ yo) y por
   `canResolveReport` (el organizador suspendido vía `suspendOrganizer` nunca es el admin). Siguen los chequeos de dueño
   duplicados (ver punto 1).
   ~~`EventController.detail`~~ **Resuelto**: `@PreAuthorize("@eventSecurity.canView(#id, authentication)")`; `canView` acepta
   anónimos; `ErrorAdvice.accessDenied` relanza si no hay sesión (Spring Security manda a /login y vuelve; probado) y da 404
   con sesión. `requireViewable` se borró. La consulta repetida de la inscripción en eventos privados (regla + handler) **la
   aceptó el docente** (30/10): "siempre primero la seguridad".
   También se limpió `detail` de lógica de negocio (corrección 06, solo para este método): las reglas de "estás inscripto /
   podés inscribirte" salieron del controller a `RegistrationService.findParticipation` (objeto `EventParticipation`) y el armado
   de la reputación del organizador a `ReviewService.findCreatorReputation` (objeto `CreatorReputation`). Verificado comparando
   el HTML del detalle antes y después en 11 escenarios (idéntico). El resto de los controllers sigue pendiente.
5. ~~Tests con `verify(`~~ **Resuelto** (se sacaron los 20 usos + 2 `verifyNoInteractions`; sin registros caseros).
   Se perdió cobertura a nivel service de "`findMessages` marca como leído" y "`send` guarda el cuerpo recortado"
   (efectos sin valor de retorno; `markRead` sí está en `EventChatJpaDaoTest`). **Sigue pendiente: reflection** en
   `MailServiceImplTest` y `ModelFixtures` (no revisado qué hacen).
6. **Roles como literales** `"ROLE_ADMIN"` sueltos (ver Modelo/tipos).
7. **Mass assignment**: sin `@InitBinder`.
8. **Auditoría JPA** (PDF `auditoria-jpa-hibernate`, no se aplicó nada, la usuaria pidió solo opinión): faltan índices
   (`event_image(event_id)`, `inbox_message(user_id,created_at)`, `registration(user_id)`, `event(creator_id)`,
   `event_invitation(user_id)`, `event_question(event_id,created_at)`, `event(event_date)`; van en `schema.sql`, no en
   migrations); `EventSimilarityServiceImpl.findSimilar` usa `findAllMatching` sin criterio en cada detalle;
   `findByEmail` repetido ~4 veces por request (`ErrorAdvice`, 2 interceptores, `EventSecurity`) → usuario de sesión con id.

## 4. Entorno local (lo que ya costó tiempo)

- **Correr:** `./mvnw -pl webapp jetty:run -Ddb.url=jdbc:postgresql://localhost/paw2026b -Ddb.username=paw -Ddb.password=paw`
  (DB local `paw2026b`, dueño `paw`, sin CREATEDB). Tras tocar `persistence`/`services*`: `./mvnw -q -o install -DskipTests`.
  App en http://localhost:8080. Superadmin: `admin@plancito.com` / `admin1234`. Cuentas de prueba (clave `123456`):
  `lucasacrosta@gmail.com`, `lola.ferreyra@…`, `tomas.rivas@…`, `malena.costa@plancito.com`; 12 eventos sembrados.
- ⚠️ **El repo está en `~/Documents` (iCloud).** Genera archivos duplicados `Foo 2.class` / `foo 2.jar` dentro de `target/`
  que disparan reinicios de Jetty y lo dejan roto (503/404 en todo). Síntoma: `find . -name "* 2.*"`. Solución: `clean`,
  o correr/probar desde una **copia fuera de `~/Documents`** (rsync excluyendo `target` y `.git`). No son código.
- JDK 24 local, el proyecto compila a 21. Mockito/ByteBuddy: `webapp` necesita `-Dnet.bytebuddy.experimental=true` en
  surefire (ya configurado) porque Hibernate trae un ByteBuddy viejo.
- Tests: `./mvnw -o test` (≈ 387 persistence / 477 services / 41 webapp). zsh: no usar `echo =====` (expande `=`).
- Para probar autorización a mano: login por curl (`-d email=… -d password=…`, cookie jar) y pedir `/eventos/{id}/editar`
  con dueño (200) y no dueño (404).

## 5. Para la defensa oral (por qué está hecho así)

- **Módulos + scope `runtime`:** convertir un error lógico (depender de la implementación) en error de compilación;
  permitió pasar de JDBC a JPA sin tocar las capas de arriba. [clase 1:L922-941]
- **Autorización en Spring Security, declarativa:** el fallo ocurre en la entrada del request, antes de ejecutar nada.
  [clase 4:L920-923] `@PreAuthorize` + bean de reglas = el "permiso dinámico por recurso" (editar solo las propias; el
  admin puede todo) que Sotuyo describe. Spring Security es un `Filter`, no un `Servlet`: corre antes de cualquier servlet.
- **404 y no 403** para eventos ajenos/privados: no se revela que existen.
- **Ownership vs visibilidad:** quién *actúa* (Security) vs quién *ve* (`canView`, depende de datos y admite anónimos).
- **Proyección para imágenes:** el permiso de cada foto se decide con 4 columnas, no cargando el `Event`.
- `@Transactional(readOnly = true)` en lecturas: hint para rutear a réplica de solo lectura.
- `@Repository`/`@Service`: especializaciones de `@Component`; `@Repository` además traduce excepciones de persistencia.
- `UserDetailsService`: patrón Strategy. Spring Security mezcla RBAC (roles) y ACL (permisos por recurso).
- CSRF deshabilitado: fuera de alcance, explícito en clase.

## 6. Material de consulta

- `~/Downloads/paw-contexto/` : `RESUMEN-CLASES.md` (resumen con referencias a línea), `texto-convertido1-4.txt`
  (transcripciones crudas), `buscar.sh "tema" [contexto]` (búsqueda rápida). **Consultar solo cuando haga falta la cita
  textual**; no releer enteras (≈ 470 KB).
- Fuera del repo (Desktop `PAW DOCUMENTACION`): devolución de TP1 (PDF), enunciados, checklists de cátedra, auditorías.
  Pedir página/sección puntual; no leer los PDFs grandes enteros.
