// Implementa el contrato RegistrationService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.exception.EventNotFoundException;
import ar.edu.itba.paw.persistence.EventDao;
import ar.edu.itba.paw.persistence.EventInvitationDao;
import ar.edu.itba.paw.persistence.RegistrationDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class RegistrationServiceImpl implements RegistrationService {

    static final int PAGE_SIZE = 10;

    private final RegistrationDao registrationDao;
    private final EventDao eventDao;
    private final NotificationService notificationService;
    private final EventInvitationDao invitationDao;
    private final InboxService inboxService;
    // Proxy propio: cancel()/revoke() llaman a promoteFromWaitlist() de esta
    // misma clase, y ese es un metodo @Transactional del contrato publico (lo
    // llaman tambien EventServiceImpl y los tests, via el proxy de siempre).
    // Una llamada directa (this implicito) no pasa por el proxy de Spring, asi
    // que ignoraria en silencio cualquier semantica de AOP que dependa de el
    // (otra propagacion, rollbackFor, etc.) si el dia de mañana cambia. Por
    // inyeccion de campo (no de constructor, a diferencia del resto de esta
    // clase): un parametro de constructor "me a mi mismo" rompe la construccion
    // manual en los tests (new RegistrationServiceImpl(...)), que no pasan por
    // un ApplicationContext. @Lazy rompe el ciclo en produccion: el bean termina
    // de construirse antes de que este campo se resuelva de verdad. Paquete-visible
    // (no private) para que el test, en el mismo paquete, lo pueda asignar
    // directo sin reflection.
    @Autowired
    @Lazy
    RegistrationService self;

    @Autowired
    public RegistrationServiceImpl(final RegistrationDao registrationDao, final EventDao eventDao,
                                    final NotificationService notificationService,
                                    final EventInvitationDao invitationDao,
                                    final InboxService inboxService) {
        this.registrationDao = registrationDao;
        this.eventDao = eventDao;
        this.notificationService = notificationService;
        this.invitationDao = invitationDao;
        this.inboxService = inboxService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegistrationResult register(final long eventId, final User user) {
        // Bloquea la fila del evento ANTES de chequear si ya existe: dos clics
        // simultaneos del mismo usuario ahora se serializan en este lock (el
        // segundo espera a que el primero termine su transaccion), en vez de
        // pasar los dos el chequeo de "existing" y recien chocar contra el
        // UNIQUE(event_id, email) al insertar -- que era lo que obligaba al
        // controller a reintentar en una segunda transaccion (ver
        // EventRegistrationController.registerRetryingOnConflict()).
        final Event event = eventDao.findByIdForUpdate(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        final Registration existing = registrationDao.findByEventAndUserId(eventId, user.getId()).orElse(null);
        if (existing != null) {
            return alreadyExistsResult(existing.getStatus());
        }

        // El creador no se anota a su propio evento: no tiene sentido pedirse
        // aprobacion a si mismo en uno con requiresApproval, ni "invitarse" a
        // uno privado del que ya es dueño. Va antes de tocar la base: no se
        // crea ninguna fila ni sale ningun mail.
        if (Long.valueOf(user.getId()).equals(event.getCreatorId())) {
            return RegistrationResult.of(RegistrationResult.Status.OWN_EVENT);
        }

        // Pendiente o rechazado (ver EventApprovalStatus): nadie se anota todavia,
        // ni siquiera un admin que lo esta revisando para decidir -- mismo motivo
        // y mismo error que el chequeo de abajo, y va primero porque
        // EventAccessServiceImpl.canView() tambien chequea esto antes que lo
        // privado/publico (sin el bypass de admin que canView() si tiene: un admin
        // puede VER el evento para revisarlo, pero no inscribirse).
        if (!event.isApproved()) {
            throw new EventNotFoundException(eventId);
        }

        // Mismo chequeo que EventAccessService.canView(), salvo el bypass de admin (un
        // admin puede VER un privado para moderarlo, pero no anotarse): quien ya tenia
        // inscripcion o es el creador ya salio por los ifs de arriba, asi que lo
        // unico que falta es la invitacion. Mismo error que un id inexistente:
        // a quien no tiene acceso no se le revela ni que el evento existe, ni si
        // esta cancelado o finalizado -- por eso va ANTES de esos chequeos.
        final boolean invited = invitationDao.findByEventAndUserId(eventId, user.getId()).isPresent();
        if (event.isPrivate() && !invited) {
            throw new EventNotFoundException(eventId);
        }

        // Un evento que ya paso no admite inscripciones. La pantalla esconde el
        // boton (ver detail.jsp), pero un POST directo no tiene que poder anotar
        // a nadie a algo que ya sucedio: es una regla de negocio, no de la web.
        if (event.isFinished()) {
            return RegistrationResult.of(RegistrationResult.Status.FINISHED);
        }

        // Evento cancelado: la pantalla ya esconde el boton, pero un POST directo
        // no tiene que poder anotar a nadie (ni mandar el mail de "confirmado").
        if (event.isCancelled()) {
            return RegistrationResult.of(RegistrationResult.Status.CANCELLED);
        }

        // Normalizado: "Test@mail.com" y "test@mail.com" son la misma persona.
        // Ya no hace falta para buscar la invitacion (eso ahora es por user_id,
        // ver mas abajo), pero si para las columnas email/name de la propia
        // inscripcion y para los avisos de bandeja (inboxService.add()).
        final String normalizedEmail = user.getEmail().trim().toLowerCase();

        // Que el evento este lleno ya no es un error: la persona entra a la lista
        // de espera. El orden de la lista sale de registration.registered_at, que
        // la base completa sola, asi que no hay ninguna posicion que guardar.
        // Quien tiene una invitacion sin usar ya tiene su lugar reservado (esa
        // invitacion esta contada en occupied()): para el, el evento no esta lleno
        // y entra confirmado sin pasar por la aprobacion del organizador, que ya
        // lo eligio al invitarlo.
        final boolean full = isFull(event, invited);
        final RegistrationStatus status = registrationStatus(event, full, invited);

        // El UNIQUE(event_id, email) sigue siendo la ultima red, pero ya no
        // deberia dispararse en el camino normal: el lock de arriba serializa
        // los clics simultaneos del mismo usuario antes de llegar aca.
        // Quien se anota siempre esta logueado (el endpoint lo exige, ver
        // EventController.rsvp()): no hace falta userService.findOrCreate() para garantizar
        // una fila en users, el Principal autenticado ya la implica.
        final Registration registration = new Registration(event, user, normalizedEmail, user.getName(), status);
        registrationDao.create(registration);

        // Tres avisos posibles, siempre en otro hilo: uno a la persona y otro al
        // dueno del evento. Lo que cambia es el texto, no la logica. Sin este if,
        // a quien quedo en lista de espera (o pendiente de revision) le llegaria
        // un mail que dice "confirmamos tu lugar", que es justo lo que no paso.
        // Y al lado de cada mail, su aviso en la bandeja (misma transaccion).
        boolean eventJustFilled = false;
        if (full) {
            notificationService.notifyWaitlistJoined(event, registration);
            notificationService.notifyNewWaitlistEntry(event, registration);
            inboxService.add(normalizedEmail, InboxMessageType.REGISTERED_WAITLISTED, eventId, null, null);
        } else if (status == RegistrationStatus.PENDING) {
            notificationService.notifyRegistrationPending(event, registration);
            notificationService.notifyNewPendingRegistration(event, registration);
            inboxService.add(normalizedEmail, InboxMessageType.REGISTERED_PENDING, eventId, null, null);
            inboxService.add(event.getCreatorEmail(), InboxMessageType.NEW_PENDING, eventId,
                    registration.getDisplayName(), null);
        } else {
            notificationService.notifyRegistrationConfirmed(event, registration);
            notificationService.notifyNewRegistration(event, registration);
            inboxService.add(normalizedEmail, InboxMessageType.REGISTERED_CONFIRMED, eventId, null, null);
            inboxService.add(event.getCreatorEmail(), InboxMessageType.NEW_REGISTRATION, eventId,
                    registration.getDisplayName(), null);

            // "Se lleno" es un CAMBIO de estado, no un estado: el aviso sale solo
            // en el pedido que ocupo el ultimo lugar. Se vuelve a preguntar DESPUES
            // del insert, porque recien ahora esta contado. Y va en esta rama
            // unicamente: si arriba ya estaba lleno, no hay nada que anunciar.
            if (isFull(event, false)) {
                notificationService.notifyEventFull(event);
                eventJustFilled = true;
            }
        }

        return new RegistrationResult(registrationResult(status), eventJustFilled);
    }

    // Se distingue el caso porque el cartel no dice lo mismo: "ya estabas
    // anotado" (tenes lugar) vs "ya estas en la lista de espera" vs "todavia
    // estan revisando tu pedido" vs "ya te habian rechazado".
    private static RegistrationResult alreadyExistsResult(final RegistrationStatus status) {
        if (status == RegistrationStatus.WAITLISTED) {
            return RegistrationResult.of(RegistrationResult.Status.ALREADY_WAITLISTED);
        }
        if (status == RegistrationStatus.PENDING) {
            return RegistrationResult.of(RegistrationResult.Status.ALREADY_PENDING);
        }
        if (status == RegistrationStatus.REJECTED) {
            return RegistrationResult.of(RegistrationResult.Status.REJECTED);
        }
        return RegistrationResult.of(RegistrationResult.Status.ALREADY_REGISTERED);
    }

    // Sin lugar (confirmados + invitaciones ya llenaron el cupo) -> lista de
    // espera: gana siempre, no tiene sentido pedirle aprobacion al organizador
    // para un lugar que no hay. Con lugar: quien tiene invitacion -> confirmed;
    // el evento pide confirmacion -> pending (no ocupa cupo hasta que lo acepten);
    // si no -> confirmed, como siempre.
    private static RegistrationStatus registrationStatus(final Event event, final boolean full,
                                                          final boolean invited) {
        if (full) {
            return RegistrationStatus.WAITLISTED;
        }
        if (invited) {
            return RegistrationStatus.CONFIRMED;
        }
        return event.isRequiresApproval() ? RegistrationStatus.PENDING : RegistrationStatus.CONFIRMED;
    }

    private static RegistrationResult.Status registrationResult(final RegistrationStatus status) {
        if (status == RegistrationStatus.WAITLISTED) {
            return RegistrationResult.Status.WAITLISTED;
        }
        if (status == RegistrationStatus.PENDING) {
            return RegistrationResult.Status.PENDING;
        }
        return RegistrationResult.Status.CONFIRMED;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFull(final long eventId) {
        final Event event = eventDao.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        return isFull(event);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFullFor(final long eventId, final long userId) {
        final Event event = eventDao.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        final boolean invited = invitationDao.findByEventAndUserId(eventId, userId).isPresent();
        return isFull(event, invited);
    }

    private boolean isFull(final Event event) {
        return isFull(event, false);
    }

    // ownInvitationHoldsSpot: la persona que consulta tiene una invitacion sin
    // usar, y esa invitacion es uno de los lugares que occupied() ya cuenta.
    private boolean isFull(final Event event, final boolean ownInvitationHoldsSpot) {
        return event.getMaxCapacity() != null
                && occupied(event.getId()) - (ownInvitationHoldsSpot ? 1 : 0) >= event.getMaxCapacity();
    }

    /**
     * Lo unico que ocupa cupo: las inscripciones CONFIRMADAS, mas las invitaciones
     * por mail a un evento privado cuya persona todavia no se anoto (cada una
     * tiene un lugar reservado; al anotarse pasa a ser su inscripcion y deja de
     * contarse aparte).
     *
     * Ni los pedidos PENDING (el organizador todavia no decidio: puede revisar
     * todos y aceptar hasta que se llene) ni la lista de espera (WAITLISTED)
     * ocupan cupo.
     */
    private long occupied(final long eventId) {
        return registrationDao.countConfirmed(eventId) + invitationDao.countOutstanding(eventId);
    }

    @Override
    @Transactional(readOnly = true)
    public long occupiedSpots(final long eventId) {
        return occupied(eventId);
    }

    @Override
    @Transactional(readOnly = true)
    public Integer availableSpots(final long eventId) {
        final Event event = eventDao.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        if (event.getMaxCapacity() == null) {
            return null;
        }
        return (int) Math.max(0, event.getMaxCapacity() - occupied(eventId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Registration> findPageByUserId(final long userId, final int page) {
        final long total = registrationDao.countByUserId(userId);
        final int effectivePage = PageResult.clampPage(page, total, PAGE_SIZE);
        return new PageResult<>(registrationDao.findByUserId(userId, effectivePage, PAGE_SIZE),
                total, effectivePage, PAGE_SIZE);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, RegistrationStatus> findStatusesByEventIds(final long userId, final Collection<Long> eventIds) {
        final Map<Long, RegistrationStatus> statuses = new HashMap<>();
        for (final Registration registration : registrationDao.findByUserIdAndEventIds(userId, eventIds)) {
            statuses.put(registration.getEventId(), registration.getStatus());
        }
        return statuses;
    }

    @Override
    @Transactional(readOnly = true)
    public long countByUserId(final long userId) {
        return registrationDao.countByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnregisterResult cancel(final long registrationId) {
        final Registration unlocked = registrationDao.findById(registrationId).orElse(null);
        if (unlocked == null) {
            return UnregisterResult.NOT_FOUND;
        }
        final long eventId = unlocked.getEventId();
        // REGLA DE ORDEN DE LOCKS (igual en register/approve/reject/revoke/cancel/
        // promote): primero la fila del evento, despues las de registration. Si una
        // operacion agarrara una inscripcion y recien despues pidiera el evento, otra
        // que ya lo tiene y quiere esa misma inscripcion la esperaria: deadlock.
        final Event event = eventDao.findByIdForUpdate(eventId).orElse(null);
        if (event == null) {
            return UnregisterResult.NOT_FOUND;
        }
        // Un evento que ya paso no se "des-anota": ya sucedio (y la inscripcion
        // es lo que habilita despues la resena). Va antes que el resto, igual que
        // lo hacia el controller cuando esta regla vivia ahi: gana incluso sobre
        // REJECTED.
        if (event.isFinished()) {
            return UnregisterResult.FINISHED;
        }
        // Se vuelve a leer ya con el lock: mientras esperaba, el organizador pudo
        // aceptarla, revocarla, etc.
        final Registration cancelled = registrationDao.findById(registrationId).orElse(null);
        if (cancelled == null) {
            return UnregisterResult.NOT_FOUND;
        }
        // Una inscripcion rechazada o revocada es un estado terminal: la fila es
        // justamente lo que le impide volver a anotarse. Si pudiera "darse de baja"
        // la borraria y se saltearia el rechazo (ademas de reactivar la invitacion
        // que hubiera tenido a un evento privado).
        if (cancelled.getStatus() == RegistrationStatus.REJECTED) {
            return UnregisterResult.REJECTED;
        }
        // Se mira ANTES de borrar: despues la fila ya no existe.
        final boolean wasConfirmed = cancelled.getStatus() == RegistrationStatus.CONFIRMED;
        final boolean wasPending = cancelled.getStatus() == RegistrationStatus.PENDING;
        // Al organizador le importa tambien si se baja alguien pendiente: tenia
        // ese pedido para responder, y ya no esta.
        final boolean organizerCares = wasConfirmed || wasPending;

        registrationDao.delete(registrationId);
        // Si se libero un lugar, el primero de la lista de espera lo ocupa enseguida.
        // Va en la misma transaccion: o pasan las dos cosas o no pasa ninguna. Devuelve
        // a cuantos movio, para poder contarselo al organizador sin volver a consultar.
        // Se libera un lugar si se iba un confirmado, o un pendiente (que en un evento
        // con aprobacion tenia un lugar reservado frente a la lista, ver reject()).
        // Que se vaya alguien de la lista de espera no libera nada.
        final int promoted = (wasConfirmed || wasPending) ? self.promoteFromWaitlist(eventId) : 0;

        // Solo se avisa si se libero un lugar de verdad. Que alguien salga de la
        // lista de espera no le cambia nada al organizador: el cupo disponible
        // era el mismo antes y despues.
        if (wasConfirmed) {
            notificationService.notifyRegistrationCancelled(event, cancelled, promoted > 0);
        }
        if (organizerCares) {
            inboxService.add(event.getCreatorEmail(), InboxMessageType.REGISTRATION_CANCELLED, eventId,
                    cancelled.getDisplayName(), null);
        }
        if (wasConfirmed) {
            return UnregisterResult.CANCELLED;
        }
        return wasPending ? UnregisterResult.CANCELLED_PENDING : UnregisterResult.CANCELLED_WAITLISTED;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnregisterResult cancelOwnRegistration(final long eventId, final long userId) {
        eventDao.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
        // Via self: cancel() es @Transactional, una llamada directa (this
        // implicito) no pasa por el proxy de Spring (mismo motivo que el
        // campo self de mas arriba).
        return registrationDao.findByEventAndUserId(eventId, userId)
                .map(r -> self.cancel(r.getId()))
                .orElse(UnregisterResult.NOT_FOUND);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Registration> findById(final long registrationId) {
        return registrationDao.findById(registrationId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Registration> findByEventAndUserId(final long eventId, final long userId) {
        return registrationDao.findByEventAndUserId(eventId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Registration> findByEventId(final long eventId) {
        return registrationDao.findByEventId(eventId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countConfirmed(final long eventId) {
        return registrationDao.countConfirmed(eventId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Registration> findPendingByEvent(final long eventId, final long creatorId) {
        if (!ownsEvent(eventId, creatorId)) {
            return Collections.emptyList();
        }
        return registrationDao.findPendingByEvent(eventId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countPendingByEvent(final long eventId, final long creatorId) {
        if (!ownsEvent(eventId, creatorId)) {
            return 0;
        }
        return registrationDao.countPending(eventId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApprovalResult approve(final long registrationId, final long creatorId) {
        final Registration unlocked = pendingOwnedBy(registrationId, creatorId).orElse(null);
        if (unlocked == null) {
            return ApprovalResult.of(ApprovalResult.Status.NOT_FOUND);
        }
        // Bloquea el evento: dos aceptaciones simultaneas no pueden pasarse del cupo.
        final Event event = eventDao.findByIdForUpdate(unlocked.getEventId()).orElse(null);
        // Un evento cancelado o que ya paso no acepta a nadie: no se confirma el pedido ni
        // sale el mail de "confirmado" de algo que no se va a hacer (o que ya sucedio).
        if (event == null || event.isCancelled() || event.isFinished()) {
            return ApprovalResult.of(ApprovalResult.Status.NOT_FOUND);
        }
        // Se vuelve a leer ya con el lock: mientras esperaba, otra aceptacion o un
        // rechazo del mismo pedido pudo haberlo cambiado (y no hay que aceptarlo, ni
        // mandar el mail, dos veces).
        final Registration registration = registrationDao.findById(registrationId)
                .filter(r -> r.getStatus() == RegistrationStatus.PENDING)
                .orElse(null);
        if (registration == null) {
            return ApprovalResult.of(ApprovalResult.Status.NOT_FOUND);
        }
        // El organizador mira todos los pedidos y acepta hasta que se llena el
        // cupo: pasado ese punto ya no puede aceptar a nadie mas.
        if (isFull(event)) {
            return ApprovalResult.of(ApprovalResult.Status.FULL);
        }
        registrationDao.updateStatus(registrationId, RegistrationStatus.CONFIRMED);
        registration.setStatus(RegistrationStatus.CONFIRMED);
        notificationService.notifyRegistrationApproved(event, registration);
        inboxService.add(registration.getEmail(), InboxMessageType.REQUEST_APPROVED, registration.getEventId(), null, null);
        // Esta aceptacion ocupo el ultimo lugar: los que seguian pendientes ya no
        // tienen a donde entrar y pasan a la lista de espera (con su mismo orden de
        // llegada), asi si alguien se baja o se revoca a alguien, entra el primero.
        boolean eventJustFilled = false;
        if (isFull(event)) {
            notificationService.notifyEventFull(event);
            eventJustFilled = true;
            moveRemainingPendingToWaitlist(event);
        }
        return new ApprovalResult(ApprovalResult.Status.APPROVED, eventJustFilled);
    }

    private void moveRemainingPendingToWaitlist(final Event event) {
        for (final Registration pending : registrationDao.findPendingByEvent(event.getId())) {
            registrationDao.updateStatus(pending.getId(), RegistrationStatus.WAITLISTED);
            pending.setStatus(RegistrationStatus.WAITLISTED);
            notificationService.notifyWaitlistJoined(event, pending);
            inboxService.add(pending.getEmail(), InboxMessageType.REGISTERED_WAITLISTED, event.getId(), null, null);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reject(final long registrationId, final long creatorId) {
        final Registration unlocked = pendingOwnedBy(registrationId, creatorId).orElse(null);
        if (unlocked == null) {
            return false;
        }
        // Evento primero (ver cancel()): ademas serializa esto con una aceptacion
        // simultanea del mismo pedido.
        final long eventId = unlocked.getEventId();
        final Event event = eventDao.findByIdForUpdate(eventId).orElse(null);
        // Idem approve(): con el evento cancelado o ya pasado no se rechaza a nadie ni se manda el mail.
        if (event == null || event.isCancelled() || event.isFinished()) {
            return false;
        }
        final Registration registration = registrationDao.findById(registrationId)
                .filter(r -> r.getStatus() == RegistrationStatus.PENDING)
                .orElse(null);
        if (registration == null) {
            return false;
        }
        registrationDao.updateStatus(registrationId, RegistrationStatus.REJECTED);
        registration.setStatus(RegistrationStatus.REJECTED);
        notificationService.notifyRegistrationRejected(event, registration);
        inboxService.add(registration.getEmail(), InboxMessageType.REQUEST_REJECTED, eventId, null, null);
        // Un pedido pendiente no ocupa cupo, pero en un evento con aprobacion si "tenia
        // reservado" un lugar frente a la lista de espera (ver promoteFromWaitlist: los
        // pendientes se descuentan de lo libre). Al rechazarlo, ese lugar vuelve a estar
        // disponible y el primero de la lista de espera pasa a ser pendiente. Sin esto
        // quedaba esperando con un lugar libre, y quien se anotara despues le pasaba por delante.
        self.promoteFromWaitlist(eventId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean revoke(final long registrationId, final long creatorId) {
        final Registration unlocked = registrationDao.findById(registrationId).orElse(null);
        if (unlocked == null) {
            return false;
        }
        // El evento se bloquea ANTES de tocar nada: escribir en la bandeja toma un
        // lock compartido sobre la fila del evento, y si dos revocaciones simultaneas
        // lo tomaran antes de pedir el lock exclusivo (en promoteFromWaitlist), cada
        // una esperaria a la otra: deadlock.
        final long eventId = unlocked.getEventId();
        final Event event = eventDao.findByIdForUpdate(eventId).orElse(null);
        // Idem approve(): con el evento cancelado o ya pasado tampoco se revoca a nadie.
        if (event == null || event.isCancelled() || event.isFinished() || !Long.valueOf(creatorId).equals(event.getCreatorId())) {
            return false;
        }
        // Se vuelve a leer ya con el lock (otra operacion pudo cambiarla mientras esperaba).
        final Registration registration = registrationDao.findById(registrationId)
                .filter(r -> r.getStatus() == RegistrationStatus.CONFIRMED)
                .orElse(null);
        if (registration == null) {
            return false;
        }
        registrationDao.updateStatus(registrationId, RegistrationStatus.REJECTED);
        registration.setStatus(RegistrationStatus.REJECTED);
        notificationService.notifyRegistrationRevoked(event, registration);
        inboxService.add(registration.getEmail(), InboxMessageType.REGISTRATION_REVOKED, eventId, null, null);
        // El lugar que quedo libre se lo lleva el primero de la lista de espera.
        self.promoteFromWaitlist(eventId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int promoteFromWaitlist(final long eventId) {
        // FOR UPDATE: dos bajas simultaneas se serializan aca y no le dan el mismo
        // lugar a dos personas.
        final Event event = eventDao.findByIdForUpdate(eventId).orElse(null);
        if (event == null || event.isCancelled()
                || (event.getEventDate() != null && event.getEventDate().before(new Date()))) {
            return 0;
        }
        // En un evento con confirmacion del organizador, quien sale de la lista de
        // espera NO entra confirmado: pasa a PENDING, como cualquiera que se anota
        // con lugar, y el organizador lo acepta o lo rechaza. Como los pendientes no
        // ocupan cupo, se descuentan los que ya esperan una respuesta: un lugar libre
        // ya "prometido" a un pedido pendiente no se le da tambien a la lista.
        final boolean toPending = event.isRequiresApproval();
        final long free = event.getMaxCapacity() == null
                ? Integer.MAX_VALUE
                : Math.max(0, event.getMaxCapacity() - occupied(eventId)
                        - (toPending ? registrationDao.countPending(eventId) : 0));
        if (free == 0) {
            return 0;
        }
        final List<Registration> next = registrationDao.findNextWaitlisted(eventId, (int) free);
        for (final Registration registration : next) {
            if (toPending) {
                registrationDao.updateStatus(registration.getId(), RegistrationStatus.PENDING);
                registration.setStatus(RegistrationStatus.PENDING);
                notificationService.notifyRegistrationPending(event, registration);
                notificationService.notifyNewPendingRegistration(event, registration);
                inboxService.add(registration.getEmail(), InboxMessageType.REGISTERED_PENDING, eventId, null, null);
                inboxService.add(event.getCreatorEmail(), InboxMessageType.NEW_PENDING, eventId,
                        registration.getDisplayName(), null);
            } else {
                registrationDao.updateStatus(registration.getId(), RegistrationStatus.CONFIRMED);
                registration.setStatus(RegistrationStatus.CONFIRMED);
                notificationService.notifyWaitlistPromoted(event, registration);
                inboxService.add(registration.getEmail(), InboxMessageType.WAITLIST_PROMOTED, eventId, null, null);
                inboxService.add(event.getCreatorEmail(), InboxMessageType.NEW_REGISTRATION, eventId,
                        registration.getDisplayName(), null);
            }
        }
        return next.size();
    }

    // Busca una inscripcion pendiente y confirma, en el mismo paso, que quien
    // pide aprobarla/rechazarla es el creador del evento al que pertenece.
    // Optional.empty() cubre los tres motivos de "no" (no existe, no esta
    // pending, o es de otro creador) sin distinguirlos: a quien llama no le
    // interesa por que no se pudo, solo que no se pudo.
    private Optional<Registration> pendingOwnedBy(final long registrationId, final long creatorId) {
        return registrationDao.findById(registrationId)
                .filter(r -> r.getStatus() == RegistrationStatus.PENDING)
                .filter(r -> ownsEvent(r.getEventId(), creatorId));
    }

    private boolean ownsEvent(final long eventId, final long creatorId) {
        return eventDao.findById(eventId)
                .map(event -> Long.valueOf(creatorId).equals(event.getCreatorId()))
                .orElse(false);
    }
}
