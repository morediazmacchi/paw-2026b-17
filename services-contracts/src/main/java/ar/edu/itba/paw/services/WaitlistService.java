// Contrato de servicio para la lista de espera. Implementacion:
// WaitlistServiceImpl (modulo services).
package ar.edu.itba.paw.services;

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
     */
    void reconcile(long eventId);

    /**
     * Cuantos cupos de este evento estan reservados por invitaciones que todavia
     * nadie contesto. Se restan del cupo igual que los confirmados: mientras
     * alguien decide, su lugar no se le puede dar a otro.
     */
    long countReserved(long eventId);

    /**
     * La invitacion de ese token, para dibujar la pagina del link. Solo lee: no
     * acepta ni rechaza nada.
     */
    Optional<InvitationDetail> findByToken(String token);

    /** La persona dijo que si desde el link del mail. */
    InvitationResult accept(String token);

    /** La persona dijo que no: sale de la lista y se llama al siguiente. */
    InvitationResult decline(String token);
}
