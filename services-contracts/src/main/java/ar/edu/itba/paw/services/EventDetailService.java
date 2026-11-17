// Arma el detalle de un evento. Implementacion: EventDetailServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface EventDetailService {

    /**
     * El detalle del evento para quien lo mira ({@code viewerId} null = sin sesion): cupo, su relacion con
     * el evento, si es favorito, si esta en el chat, la reputacion del organizador, los eventos
     * relacionados y una pagina de preguntas. Los eventos relacionados son solo de un evento publico y
     * aprobado. Que se pueda ver el evento NO se valida aca: lo decide @PreAuthorize en el controller (ver
     * EventSecurity.canView). Vacio si el evento no existe (el 404 lo da la capa web).
     */
    @Transactional(readOnly = true)
    Optional<EventDetail> getDetail(long eventId, Long viewerId, int questionsPage, int reviewsPage);
}
