// Contrato de servicio para seguir organizadores. Implementacion: FollowServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.FollowerContact;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface FollowService {

    /**
     * Sigue al organizador de ese evento: a partir de ahora, cuando publique un evento nuevo y quede
     * aprobado, le llega un aviso (mail + bandeja, ver EventServiceImpl.approve()). El organizador sale del
     * evento, no de un id que mande el cliente. Quien puede (ve el evento y no es su organizador, o sea, no
     * se sigue a si mismo) lo decide @PreAuthorize en el controller (ver EventSecurity.canInteract).
     *
     * @return true si empezo a seguirlo ahora; false si ya lo seguia (idempotente) o si el evento no
     *         tiene organizador cargado (eventos viejos).
     */
    @Transactional(rollbackFor = Exception.class)
    boolean followOrganizerOf(long eventId, long followerId);

    /**
     * Deja de seguir al organizador de ese evento.
     *
     * @return true si lo seguia; false si no (o si el evento no tiene organizador cargado).
     */
    @Transactional(rollbackFor = Exception.class)
    boolean unfollowOrganizerOf(long eventId, long followerId);

    @Transactional(readOnly = true)
    boolean isFollowing(long followerId, long followedId);

    /**
     * A quienes mandarles el mail cuando este usuario publique un evento nuevo (ver EventServiceImpl.approve()):
     * solo mail, nombre e idioma de cada uno, no la cuenta entera.
     */
    @Transactional(readOnly = true)
    List<FollowerContact> findFollowers(long followedId);

    /** Cuantos lo siguen (el contador de "Mi perfil"). */
    @Transactional(readOnly = true)
    long countFollowers(long followedId);
}
