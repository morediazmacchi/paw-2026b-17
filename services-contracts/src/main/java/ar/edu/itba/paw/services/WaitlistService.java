// Contrato de servicio para la lista de espera. Implementacion:
// WaitlistServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import java.util.List;
import java.util.Optional;

public interface WaitlistService {

    /**
     * Pone al dia la lista de espera de un evento: vence las invitaciones a las
     * que se les paso la hora y le ofrece los cupos que queden libres a las
     * primeras personas de la lista que todavia no fueron invitadas.
     *
     * Es idempotente: llamarlo dos veces seguidas no invita a nadie de mas,
     * porque una invitacion pendiente ya reserva su cupo.
     *
     * Se llama cada vez que un cupo puede haberse liberado: una cancelacion, un
     * rechazo, o una invitacion vencida.
     *
     * @return a cuantas personas se les ofrecio un lugar en esta pasada. Quien
     *         llama lo usa para contar que paso (por ejemplo, para avisarle al
     *         organizador si el lugar que se libero ya tiene candidato o quedo
     *         abierto), en vez de tener que adivinarlo con otra consulta.
     */
    int reconcile(long eventId);

    /**
     * Cuantos cupos de este evento estan reservados por invitaciones que todavia
     * nadie contesto. Se restan del cupo igual que los confirmados: mientras
     * alguien decide, su lugar no se le puede dar a otro.
     */
    long countReserved(long eventId);

    /**
     * Los eventos que la tarea periodica tiene que revisar: futuros, con cupo
     * limitado y con gente esperando. El resto del tiempo reconcile() se dispara
     * solo, cuando alguien cancela, acepta o rechaza; esto cubre el unico caso en
     * el que nadie aprieta nada: una invitacion que se vence sola.
     */
    List<Long> findEventsNeedingReconcile();

    /**
     * La invitacion de esta persona (email del usuario logueado) para este
     * evento, para dibujar la pagina del link. Solo lee: no acepta ni rechaza nada.
     */
    Optional<InvitationDetail> findForUser(long eventId, String email);

    /** La persona dijo que si desde el link del mail. */
    InvitationResult accept(long eventId, String email);

    /** La persona dijo que no: sale de la lista y se llama al siguiente. */
    InvitationResult decline(long eventId, String email);
}
