// Contrato de servicio para inscripciones. Implementacion: RegistrationServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationAccess;
import ar.edu.itba.paw.model.RegistrationStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// @Transactional aca documenta la garantia que promete cada metodo (cual lee
// nada mas, cual puede escribir y bajo que regla de rollback). La implementacion
// real de esa garantia la arma el proxy de Spring sobre RegistrationServiceImpl
// (ver @EnableTransactionManagement en WebConfig), no esta anotacion por si sola.
public interface RegistrationService {

    /**
     * Anota a alguien en un evento. Si ya estaba anotado con ese usuario, no crea uno nuevo.
     *
     * Si el evento esta lleno (confirmados + invitaciones a un evento privado
     * ya alcanzaron el cupo) la inscripcion igual se crea, pero en estado
     * "waitlisted": la persona queda en la lista de espera, ordenada por el
     * momento en que se anoto. Si no esta lleno y el evento pide confirmacion,
     * queda "pending" hasta que el creador la acepte. Quien tiene una invitacion
     * al evento privado entra directo confirmada: su lugar ya estaba reservado.
     *
     * Si el evento ya paso devuelve FINISHED y no crea nada.
     * OJO: este metodo NO valida que el evento se pueda ver ni que la persona pueda anotarse
     * (aprobado y, si es privado, con invitacion). Eso es AUTORIZACION y la resuelve
     * @PreAuthorize en el controller (ver EventSecurity.canRegister).
     *
     * @throws EventNotFoundException si el evento no existe.
     * @throws UserNotFoundException si la cuenta no existe (se lee de la base: la sesion solo lleva su id).
     */
    @Transactional(rollbackFor = Exception.class)
    RegistrationResult register(long eventId, long userId);

    /**
     * @return true si el evento ya alcanzo su cupo maximo (siempre false si no tiene limite)
     */
    @Transactional(readOnly = true)
    boolean isFull(long eventId);

    /**
     * Igual que isFull(eventId) pero desde el punto de vista de esa persona: si
     * tiene una invitacion sin usar, su propio lugar esta reservado y para ella
     * el evento no esta lleno. Quien pregunta ya esta logueado (por eso userId,
     * no email: ver EventInvitationService.isInvited).
     */
    @Transactional(readOnly = true)
    boolean isFullFor(long eventId, long userId);

    /**
     * @return cupos que todavia quedan libres (cupo maximo menos ocupados, ver
     *         occupiedSpots()), o null si el evento no tiene limite de cupo
     */
    @Transactional(readOnly = true)
    Integer availableSpots(long eventId);

    /**
     * Cupos que ya no se le pueden dar a otro: confirmados + invitaciones por
     * mail a un evento privado cuya persona todavia no se anoto. Ni los pedidos
     * pendientes de aprobacion ni la lista de espera ocupan cupo. Es la misma
     * cuenta que usan isFull() y availableSpots().
     */
    @Transactional(readOnly = true)
    long occupiedSpots(long eventId);

    /**
     * "Mis inscripciones": las proximas y las pasadas del usuario, cada grupo paginado por su cuenta (con su
     * propio indice de pagina, que empieza en 1), cada inscripcion con su evento y, en las pasadas, la reseña
     * propia. Proximas: eventos que todavia no pasaron, el mas proximo primero. Pasadas: el mas reciente
     * primero. La division se hace en la base, con el mismo "ahora" para los dos grupos. Un indice mayor a la
     * ultima pagina de su grupo devuelve la ultima, uno menor a 1 la primera; un grupo vacio, la pagina 1 vacia.
     */
    @Transactional(readOnly = true)
    MyRegistrationsView findMyRegistrations(long userId, int upcomingPage, int pastPage);

    /** Para el badge de "Mis inscripciones" (ver ProfileController): sin traer las filas. */
    @Transactional(readOnly = true)
    long countByUserId(long userId);

    /**
     * Cancela la inscripcion. Devuelve NOT_FOUND (y no borra nada) si no existe, y FINISHED
     * (tampoco borra nada) si el evento ya paso.
     * OJO: este metodo NO valida de quien es la inscripcion. Que solo se pueda cancelar la propia
     * es AUTORIZACION y la resuelve @PreAuthorize en el controller (ver EventSecurity.isRegistrant).
     */
    @Transactional(rollbackFor = Exception.class)
    UnregisterResult cancel(long registrationId);

    /**
     * Como {@link #cancel(long)}, pero para quien no tiene el id de la
     * inscripcion a mano, solo el del evento (ver EventRegistrationController.unregister(),
     * que da de baja desde el detalle en vez de desde "Mis inscripciones"): busca la
     * inscripcion propia de ese evento y la cancela.
     *
     * @throws EventNotFoundException si el evento no existe.
     */
    @Transactional(rollbackFor = Exception.class)
    UnregisterResult cancelOwnRegistration(long eventId, long userId);

    /**
     * La inscripcion con ese id, sin mirar quien pregunta. Lo usa la capa web
     * (EventSecurity) para decidir si el organizador puede actuar sobre ella.
     */
    @Transactional(readOnly = true)
    Optional<Registration> findById(long registrationId);

    /**
     * Lo minimo de la inscripcion para decidir un permiso (de quien es, de que evento, en que estado):
     * una consulta de 3 columnas, sin cargar la Registration. Lo usan las reglas de EventSecurity.
     */
    Optional<RegistrationAccess> findAccessById(long registrationId);

    /** Igual que {@link #findAccessById(long)}, pero buscando por evento y usuario. */
    Optional<RegistrationAccess> findAccessByEventAndUserId(long eventId, long userId);

    /**
     * Para el detalle de evento: si el usuario logueado ya esta anotado a
     * este evento, para mostrar "Darme de baja" en vez de "Inscribirme".
     */
    @Transactional(readOnly = true)
    Optional<Registration> findByEventAndUserId(long eventId, long userId);

    /**
     * El estado de la inscripcion de esta persona en cada uno de esos eventos, en una sola
     * consulta (para etiquetar un listado, ver EventController.list()). Los eventos en los que no
     * tiene inscripcion (o se dio de baja: la fila se borra) no aparecen en el mapa.
     */
    @Transactional(readOnly = true)
    Map<Long, RegistrationStatus> findStatusesByEventIds(long userId, Collection<Long> eventIds);

    /**
     * Los que tienen el lugar confirmado en un evento, en orden de llegada: a quienes se les manda el mail
     * cuando el evento cambia, se cancela o es al dia siguiente. El filtro lo hace la base. Solo lo usan otros
     * services (no se muestra en ninguna pantalla).
     */
    @Transactional(readOnly = true)
    List<Registration> findConfirmedByEventId(long eventId);

    /**
     * Lo mismo que findByEventId pero de a una pagina, para las pantallas "Ver inscriptos" del
     * organizador y del admin. Mismo requisito: quien llama verifica antes quien pregunta. Una pagina
     * mayor a la ultima devuelve la ultima, una menor a 1 la primera; sin inscriptos, la pagina 1 vacia.
     */
    @Transactional(readOnly = true)
    PageResult<Registration> findPageByEventId(long eventId, int page);

    /**
     * Pantalla de "aceptar o rechazar inscriptos" del creador: las pendientes
     * de este evento, de la que espera hace mas tiempo a la mas reciente.
     * Que quien lo pide sea el creador NO se valida aca: lo decide @PreAuthorize en el
     * controller (ver EventSecurity.isOwner).
     */
    @Transactional(readOnly = true)
    List<Registration> findPendingByEvent(long eventId);

    /**
     * La pantalla de aceptar o rechazar inscriptos: UNA pagina de las pendientes (en el orden de
     * findPendingByEvent()), el total de pendientes, los lugares libres y si el evento ya esta lleno, en una
     * sola llamada. Una pagina mayor a la ultima devuelve la ultima, una menor a 1 la primera; sin pendientes,
     * la pagina 1 vacia. Mismo OJO que findPendingByEvent(): quien llama verifica antes quien pregunta.
     */
    @Transactional(readOnly = true)
    PendingRegistrations findPendingSummary(long eventId, int page);

    /**
     * Cuantos pedidos pendientes tiene cada uno de esos eventos, en una sola consulta (un GROUP BY). Un
     * evento sin pendientes no aparece en el mapa.
     */
    @Transactional(readOnly = true)
    Map<Long, Long> countPendingByEvents(Collection<Long> eventIds);

    /** Igual que countPendingByEvents(), pero de confirmados. */
    @Transactional(readOnly = true)
    Map<Long, Long> countConfirmedByEvents(Collection<Long> eventIds);

    /**
     * El creador acepta una inscripcion pendiente: pasa a "confirmed" y se le
     * avisa a la persona por mail. Solo se puede mientras quede lugar: si el
     * evento ya esta lleno devuelve FULL sin tocar nada. Cuando esta aceptacion
     * ocupa el ultimo lugar, todos los pedidos que seguian pendientes pasan solos
     * a la lista de espera. Que quien lo hace sea el creador del evento NO se valida aca: lo decide
     * @PreAuthorize en el controller (ver EventSecurity.isOwnerOfRegistrationEvent).
     */
    @Transactional(rollbackFor = Exception.class)
    ApprovalResult approve(long registrationId);

    /**
     * El creador rechaza una inscripcion pendiente: pasa a "rejected" (queda
     * la fila, no se puede volver a pedir lugar en este evento con esa cuenta)
     * y se le avisa a la persona por mail. Devuelve false (sin tocar nada) si la
     * inscripcion no existe o no esta pendiente. Que quien lo hace sea el creador NO se valida
     * aca: lo decide @PreAuthorize en el controller.
     */
    @Transactional(rollbackFor = Exception.class)
    boolean reject(long registrationId);

    /**
     * El creador le saca el lugar a alguien ya confirmado: pasa a "rejected"
     * (no puede volver a anotarse), se le avisa por mail y el lugar libre le
     * toca al siguiente de la lista de espera. Devuelve false (sin tocar nada) si
     * la inscripcion no existe o no esta confirmada. Que quien lo hace sea el creador NO se valida
     * aca: lo decide @PreAuthorize en el controller.
     */
    @Transactional(rollbackFor = Exception.class)
    boolean revoke(long registrationId);

    /**
     * Si el evento tiene lugares libres, saca de la lista de espera (en orden de
     * llegada) a tantas personas como lugares haya. En un evento normal quedan
     * CONFIRMADAS; en uno que requiere confirmacion del organizador pasan a
     * PENDING (el organizador todavia tiene que aceptarlas) y solo se cuentan los
     * lugares que no estan ya esperando una respuesta. Devuelve a cuantas movio.
     * Se llama sola cuando se libera un lugar (baja, revocacion) o cambia el cupo.
     */
    @Transactional(rollbackFor = Exception.class)
    int promoteFromWaitlist(long eventId);
}
