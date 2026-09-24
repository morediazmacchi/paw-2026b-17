// Contrato de servicio para inscripciones. Implementacion: RegistrationServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Registration;

import java.util.List;
import java.util.Optional;

public interface RegistrationService {

    /**
     * Anota a alguien en un evento. Si ya estaba anotado con ese mail, no crea uno nuevo.
     *
     * Si el evento esta lleno la inscripcion igual se crea, pero en estado
     * "waitlisted": la persona queda en la lista de espera, ordenada por el
     * momento en que se anoto.
     */
    RegistrationResult register(long eventId, String email, String name);

    /**
     * @return true si el evento ya alcanzo su cupo maximo (siempre false si no tiene limite)
     */
    boolean isFull(long eventId);

    /**
     * @return cupos que todavia quedan libres (cupo maximo menos confirmados),
     *         o null si el evento no tiene limite de cupo
     */
    Integer availableSpots(long eventId);

    /**
     * Cupos que ya no se le pueden dar a otro: confirmados + invitaciones de la
     * lista de espera sin contestar + pedidos pendientes de aprobacion. Es la
     * misma cuenta que usan isFull() y availableSpots().
     */
    long occupiedSpots(long eventId);

    /**
     * "Mis inscripciones": las de ese email, mas recientes primero.
     */
    List<Registration> findByEmail(String email);

    /**
     * Cancela una inscripcion propia. Devuelve false (y no borra nada) si no
     * existe o si es de otro email: cancelar la inscripcion de otra persona
     * conociendo solo su id no tiene que ser posible.
     */
    boolean cancel(long registrationId, String email);

    /**
     * Para el detalle de evento: si el usuario logueado ya esta anotado a
     * este evento, para mostrar "Darme de baja" en vez de "Inscribirme".
     */
    Optional<Registration> findByEventAndEmail(long eventId, String email);

    /**
     * "Ver inscriptos" del organizador: todos los anotados a un evento, en
     * orden de llegada.
     *
     * OJO: este metodo NO valida quien pregunta -- a diferencia de cancel(),
     * que solo deja cancelar tu propia inscripcion, este eventId es un
     * parametro arbitrario (viene de la URL, no del usuario logueado como
     * findByEmail). Quien llama (EventController.inscriptos()) tiene que
     * verificar que el que pregunta sea el creador del evento ANTES de
     * mostrar esta lista.
     */
    List<Registration> findByEventId(long eventId);

    /**
     * Pantalla de "aceptar o rechazar inscriptos" del creador: las pendientes
     * de este evento, de la que espera hace mas tiempo a la mas reciente.
     * Devuelve vacio si el evento no existe o no es de ese email (no revela
     * cual de las dos cosas paso).
     */
    List<Registration> findPendingByEvent(long eventId, String creatorEmail);

    /**
     * El creador acepta una inscripcion pendiente: pasa a "confirmed" y se le
     * avisa a la persona por mail. Devuelve false (sin tocar nada) si la
     * inscripcion no existe, no esta pendiente, o el evento no es de ese email.
     */
    boolean approve(long registrationId, String creatorEmail);

    /**
     * El creador rechaza una inscripcion pendiente: pasa a "rejected" (queda
     * la fila, no se puede volver a pedir lugar en este evento con ese mail)
     * y se le avisa a la persona por mail. Mismas condiciones de false que approve().
     */
    boolean reject(long registrationId, String creatorEmail);
}
