// Contrato de servicio para Event. Implementacion: EventServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventSearchCriteria;

import java.util.List;
import java.util.Optional;

public interface EventService {
    Event create(Event event);
    Optional<Event> findById(long id);
    List<Event> findAll();
    List<Event> findByCategory(long categoryId);
    // Pagina la respuesta: criteria.getPage() puede caer a la ultima pagina con
    // resultados si pedis una que no existe.
    PageResult<Event> search(EventSearchCriteria criteria);

    // "Mis eventos": todos los creados por este usuario, mas recientes primero.
    List<Event> findByCreatorId(long creatorId);

    /**
     * Edita un evento existente. No cambia quien lo creo ni cuando se creo
     * (esos campos de "changes" se ignoran).
     *
     * @param changes los valores nuevos; su propio id se ignora, se usa el
     *                eventId del primer parametro
     */
    EventUpdateResult update(long eventId, Event changes, long callerId);

    /**
     * Cancela (soft-delete) un evento existente y avisa por mail a cada
     * inscripto confirmado.
     */
    EventCancelResult cancel(long eventId, long callerId);

    /**
     * Baja por moderacion (ver ModerationService): mismo efecto que cancel()
     * para quien esta anotado (mismos avisos a los inscriptos), pero sin
     * chequeo de ownership (lo pide un admin, no el creador) y ademas le
     * avisa al creador con el motivo del reporte.
     */
    EventCancelResult removeByAdmin(long eventId, String reason);
}
