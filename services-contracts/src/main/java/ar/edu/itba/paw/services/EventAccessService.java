// Contrato de servicio para EventAccessService. Implementacion: EventAccessServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventAccessData;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.exception.EventCancelledException;
import ar.edu.itba.paw.model.exception.EventNotFoundException;

import java.util.Optional;

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
     */
    boolean canView(EventAccessData event, User user, Optional<Registration> myRegistration);

    /**
     * Como {@link #canView(EventAccessData, User, Optional)}, pero tira EventNotFoundException en vez de
     * devolver false. Para quien ya tiene el Event y el Optional&lt;Registration&gt; en mano (ver
     * EventController.detail(), que los necesita de nuevo mas abajo para la tarjeta de RSVP) y
     * no quiere armar el if/throw el mismo -- esa decision le corresponde al service, no al
     * controller.
     *
     * @throws EventNotFoundException si no puede verlo. Mismo error que si no existiera.
     */
    void requireViewable(Event event, User user, Optional<Registration> myRegistration);

    /**
     * Igual que {@link #canView(EventAccessData, User, Optional)}, pero busca la inscripcion propia solo si hace
     * falta (un evento publico lo ve cualquiera, ni se consulta). {@code user} puede ser null (sin sesion).
     */
    boolean canView(EventAccessData event, User user);

    /**
     * El evento, si existe y {@code user} puede verlo.
     *
     * @throws EventNotFoundException si no existe o no puede verlo. Es el mismo error en los dos casos:
     *                                no se le confirma a nadie que un evento privado existe.
     */
    Event getViewableEvent(long eventId, User user);

    /**
     * El evento, si no esta cancelado: uno cancelado no se edita. Lo comparten el GET y el POST de
     * EventController para editar (editarForm()/editar()): misma regla, mismo lugar.
     * Quien es el creador NO se valida aca: eso es autorizacion y lo resuelve @PreAuthorize en el
     * controller (ver EventSecurity). Esto es solo la regla de estado del evento.
     *
     * @throws EventNotFoundException si no existe.
     * @throws EventCancelledException si existe pero esta cancelado.
     */
    Event getEditableEvent(long eventId);
}
