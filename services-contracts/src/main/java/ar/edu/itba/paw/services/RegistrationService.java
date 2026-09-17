// Contrato de servicio para inscripciones. Implementacion: RegistrationServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Registration;

import java.util.List;

public interface RegistrationService {

    /**
     * Anota a alguien en un evento. Si ya estaba anotado con ese mail, no crea uno nuevo.
     *
     * @throws IllegalStateException si el evento ya esta lleno
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
     * "Mis inscripciones": las de ese email, mas recientes primero.
     */
    List<Registration> findByEmail(String email);

    /**
     * Cancela una inscripcion propia. Devuelve false (y no borra nada) si no
     * existe o si es de otro email: cancelar la inscripcion de otra persona
     * conociendo solo su id no tiene que ser posible.
     */
    boolean cancel(long registrationId, String email);
}
