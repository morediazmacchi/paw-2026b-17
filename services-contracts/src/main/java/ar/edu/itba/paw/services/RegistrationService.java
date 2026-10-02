// Contrato de servicio para inscripciones. Implementacion: RegistrationServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.User;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
     */
    @Transactional(rollbackFor = Exception.class)
    RegistrationResult register(long eventId, User user);

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
     * "Mis inscripciones": las de ese usuario, mas recientes primero.
     */
    @Transactional(readOnly = true)
    List<Registration> findByUserId(long userId);

    /**
     * Cancela una inscripcion propia. Devuelve NOT_FOUND (y no borra nada) si
     * no existe o si es de otro usuario: cancelar la inscripcion de otra
     * persona conociendo solo su id no tiene que ser posible.
     */
    @Transactional(rollbackFor = Exception.class)
    UnregisterResult cancel(long registrationId, long userId);

    /**
     * Para el detalle de evento: si el usuario logueado ya esta anotado a
     * este evento, para mostrar "Darme de baja" en vez de "Inscribirme".
     */
    @Transactional(readOnly = true)
    Optional<Registration> findByEventAndUserId(long eventId, long userId);

    /**
     * "Ver inscriptos" del organizador: todos los anotados a un evento, en
     * orden de llegada.
     *
     * OJO: este metodo NO valida quien pregunta -- a diferencia de cancel(),
     * que solo deja cancelar tu propia inscripcion, este eventId es un
     * parametro arbitrario (viene de la URL, no del usuario logueado como
     * findByUserId). Quien llama (EventController.inscriptos()) tiene que
     * verificar que el que pregunta sea el creador del evento ANTES de
     * mostrar esta lista.
     */
    @Transactional(readOnly = true)
    List<Registration> findByEventId(long eventId);

    /**
     * Pantalla de "aceptar o rechazar inscriptos" del creador: las pendientes
     * de este evento, de la que espera hace mas tiempo a la mas reciente.
     * Devuelve vacio si el evento no existe o no es de ese creador (no revela
     * cual de las dos cosas paso).
     */
    @Transactional(readOnly = true)
    List<Registration> findPendingByEvent(long eventId, long creatorId);

    /**
     * El creador acepta una inscripcion pendiente: pasa a "confirmed" y se le
     * avisa a la persona por mail. Solo se puede mientras quede lugar: si el
     * evento ya esta lleno devuelve FULL sin tocar nada. Cuando esta aceptacion
     * ocupa el ultimo lugar, todos los pedidos que seguian pendientes pasan solos
     * a la lista de espera.
     */
    @Transactional(rollbackFor = Exception.class)
    ApprovalResult approve(long registrationId, long creatorId);

    /**
     * El creador rechaza una inscripcion pendiente: pasa a "rejected" (queda
     * la fila, no se puede volver a pedir lugar en este evento con esa cuenta)
     * y se le avisa a la persona por mail. Devuelve false (sin tocar nada) si la
     * inscripcion no existe, no esta pendiente, o el evento no es de ese creador.
     */
    @Transactional(rollbackFor = Exception.class)
    boolean reject(long registrationId, long creatorId);

    /**
     * El creador le saca el lugar a alguien ya confirmado: pasa a "rejected"
     * (no puede volver a anotarse), se le avisa por mail y el lugar libre le
     * toca al siguiente de la lista de espera. Devuelve false (sin tocar nada) si
     * la inscripcion no existe, no esta confirmada, o el evento no es de ese creador.
     */
    @Transactional(rollbackFor = Exception.class)
    boolean revoke(long registrationId, long creatorId);

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
