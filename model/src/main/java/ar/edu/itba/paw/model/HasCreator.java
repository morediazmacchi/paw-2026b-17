package ar.edu.itba.paw.model;

/**
 * Algo que tiene un organizador (un evento, o la proyeccion de a que evento apunta un reporte) y las reglas que se
 * deducen de eso, en UN solo lugar: "es el creador" y "un admin no actua sobre lo propio". Las usan los services, la
 * autorizacion (EventSecurity, AdminSecurity) y las vistas de admin, asi no hay copias de la comparacion.
 */
public interface HasCreator {

    // null solo en una fila vieja que todavia no paso por el backfill de postgres-migrations.sql.
    Long getCreatorId();

    // La unica comparacion "esta persona es el creador". Estatica para quien solo tiene los dos ids (ej. la
    // autorizacion de responder una pregunta, que lee solo el id del organizador).
    static boolean isCreator(final Long userId, final Long creatorId) {
        return userId != null && userId.equals(creatorId);
    }

    // La persona es la organizadora.
    default boolean isCreatedBy(final Long userId) {
        return isCreator(userId, getCreatorId());
    }

    // Un admin puede aprobar/rechazar/resolver reportes sobre esto salvo que sea suyo (conflicto de intereses: el
    // creador no puede ser tambien quien lo revisa). Es la regla de AdminSecurity.canModerateEvent() y
    // canResolveReport().
    default boolean canBeModeratedBy(final Long adminId) {
        return !isCreatedBy(adminId);
    }
}
