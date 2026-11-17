// Contrato de servicio para EventAccessService. Implementacion: EventAccessServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import java.util.Optional;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventAccessData;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.exception.EventCancelledException;
import ar.edu.itba.paw.model.exception.EventRejectedException;

public interface EventAccessService {

    /**
     * Un evento publico lo puede ver cualquiera, como siempre. Uno privado solo
     * su creador, alguien que ya tenga una fila de inscripcion (en cualquier
     * estado, incluido rechazado: si te rechazaron es porque en algun momento
     * tuviste acceso, no tiene sentido que ahora te vuelvas a topar con un 404),
     * o alguien cuya cuenta (la de su sesion) fue invitada. El link del mail solo
     * dice que evento es: quien sos lo dice tu sesion.
     * Recibe EventAccessData (lo implementan Event y la proyeccion EventAccessSummary) para poder
     * decidir, por ejemplo al servir una imagen, sin cargar el Event entero.
     * Lo comparten el detalle del evento (EventController), las imagenes y los services de
     * preguntas y favoritos (via EventSecurity y directamente): por eso vive en un service aparte y
     * no como metodo privado de un solo controller.
     * OJO: esto es VISIBILIDAD (quien puede ver un evento). La AUTORIZACION de las acciones del
     * organizador (editar, cancelar, ver inscriptos, invitar...) no esta aca: la resuelve
     * @PreAuthorize en los controllers (ver EventSecurity).
     * La inscripcion propia se busca solo si hace falta (un evento publico lo ve cualquiera, ni se
     * consulta). {@code user} puede ser null (sin sesion).
     */
    boolean canView(EventAccessData event, User user);

    /**
     * Igual que {@link #canView(EventAccessData, User)}, pero para quien no tiene la entidad User
     * a mano, solo su id y si es admin: es lo que lleva el principal de la sesion (ver
     * PlancitoUserDetails), asi decidir no obliga a buscar al usuario en la base.
     * {@code userId} null = sin sesion.
     */
    boolean canView(EventAccessData event, Long userId, boolean isAdmin);

    /**
     * Se puede anotar a ese evento: la regla de {@link #canView(EventAccessData, Long, boolean)} SIN la excepcion
     * de admin. Un admin puede VER un evento privado o pendiente para moderarlo, pero no inscribirse; si lo
     * invitaron a un privado, la invitacion cuenta como a cualquiera. Es la unica definicion de esta regla: la
     * usan la autorizacion (EventSecurity.canRegister) y el detalle (EventDetailService, para decidir que se
     * le ofrece). {@code userId} null (sin sesion) nunca puede, y el organizador tampoco: no se anota a su
     * propio evento.
     */
    boolean canRegister(EventAccessData event, Long userId);

    /**
     * El evento, si se puede editar (ver Event.isEditable()): uno cancelado o rechazado no se edita. Lo comparten
     * el GET de EventController para editar y EventService.update(): misma regla, mismo lugar.
     * Quien es el creador NO se valida aca: eso es autorizacion y lo resuelve @PreAuthorize en el
     * controller (ver EventSecurity). Esto es solo la regla de estado del evento.
     *
     * Vacio si el evento no existe (el 404 lo da la capa web).
     *
     * @throws EventCancelledException si existe pero esta cancelado.
     * @throws EventRejectedException si existe pero fue rechazado por un admin.
     */
    Optional<Event> getEditableEvent(long eventId);
}
