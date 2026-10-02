// Contrato de servicio para Event. Implementacion: EventServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.dto.EventDTO;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventSearchCriteria;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

// @Transactional aca documenta la garantia que promete cada metodo; el proxy
// de verdad lo arma Spring sobre EventServiceImpl (ver RegistrationService
// para el mismo criterio).
public interface EventService {
    /**
     * Crea un evento nuevo a partir de un DTO ya validado (ver EventForm.toDTO()):
     * el service arma el Event final y lo persiste, sin que el caller tenga que
     * tocar el modelo de dominio.
     */
    @Transactional(rollbackFor = Exception.class)
    Event create(EventDTO dto);
    @Transactional(readOnly = true)
    Optional<Event> findById(long id);

    // Una sola consulta para varios ids, para armar un mapa id->Event sin un
    // findById() por cada uno (ver AdminController.reportQueueView(),
    // EventController.addCreatorReputation()).
    @Transactional(readOnly = true)
    List<Event> findByIds(List<Long> ids);
    // Pagina la respuesta: criteria.getPage() puede caer a la ultima pagina con
    // resultados si pedis una que no existe.
    @Transactional(readOnly = true)
    PageResult<Event> search(EventSearchCriteria criteria);

    // "Mis eventos": todos los creados por este usuario, mas recientes primero.
    @Transactional(readOnly = true)
    List<Event> findByCreatorId(long creatorId);

    /**
     * Edita un evento existente. No cambia quien lo creo ni cuando se creo
     * (esos campos de "changes" se ignoran).
     *
     * @param changes los valores nuevos; su propio id se ignora, se usa el
     *                eventId del primer parametro
     */
    @Transactional(rollbackFor = Exception.class)
    EventUpdateResult update(long eventId, Event changes, long callerId);

    /**
     * Cancela (soft-delete) un evento existente y avisa por mail a cada
     * inscripto confirmado.
     */
    @Transactional(rollbackFor = Exception.class)
    EventCancelResult cancel(long eventId, long callerId);

    /**
     * Baja por moderacion (ver ModerationService): mismo efecto que cancel()
     * para quien esta anotado (mismos avisos a los inscriptos), pero sin
     * chequeo de ownership (lo pide un admin, no el creador) y ademas le
     * avisa al creador con el motivo del reporte.
     */
    @Transactional(rollbackFor = Exception.class)
    EventCancelResult removeByAdmin(long eventId, String reason);

    /**
     * Banner de /eventos (ver EventController.eventos()): los eventos que un
     * admin destaco, mas reciente primero. Ya vienen filtrados a
     * publicos/activos/futuros, igual que search().
     */
    @Transactional(readOnly = true)
    List<Event> findFeatured(int limit);

    /** Un admin marca o desmarca este evento para el banner (ver AdminController). */
    @Transactional(rollbackFor = Exception.class)
    boolean setFeatured(long eventId, boolean featured);
}
