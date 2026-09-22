// Contrato de persistencia para Event. Implementacion: EventJdbcDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventSearchCriteria;

import java.util.List;
import java.util.Optional;

public interface EventDao {
    Event create(Event event);

    // event.getId() tiene que venir seteado (identifica la fila a actualizar).
    // No toca creator_email ni created_at -- quien creo el evento y cuando no
    // se editan. Devuelve void a proposito, mismo patron que UserDao.updateProfile:
    // no hay ningun dato generado que devolver como si fuera un create().
    void update(Event event);

    // Soft-delete: marca cancelled_at = now(). No borra la fila (ver comentario
    // en schema.sql). Idempotente a nivel SQL, pero quien llama (EventService)
    // es responsable de no invocarlo dos veces sobre el mismo evento.
    void cancel(long id);

    // No excluye cancelados a proposito: el detalle de un evento cancelado
    // sigue siendo accesible para quien ya estaba anotado (ver EventController).
    Optional<Event> findById(long id);

    // SELECT ... FOR UPDATE: usar solo dentro de un @Transactional que chequee cupo.
    Optional<Event> findByIdForUpdate(long id);

    // Excluyen eventos cancelados (ver cancel() mas arriba).
    List<Event> findAll();
    List<Event> findByCategory(long categoryId);

    // "Mis eventos": todos los del creador (pasados y cancelados incluidos, a
    // diferencia de search()), mas recientes primero.
    List<Event> findByCreatorEmail(String email);

    // Combina todos los filtros con AND (un campo en null en criteria significa "sin filtrar por
    // esto"); solo eventos futuros y no cancelados; ordenado y paginado segun criteria.
    List<Event> search(EventSearchCriteria criteria);

    // Mismos filtros que search() (mismo WHERE), sin orden ni paginado: el total para el paginador.
    long countSearch(EventSearchCriteria criteria);
}
