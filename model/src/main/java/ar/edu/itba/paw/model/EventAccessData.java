package ar.edu.itba.paw.model;

// Los datos de un evento que alcanzan para decidir quien puede verlo (ver
// EventAccessService.canView). Existe para poder decidirlo sin cargar el Event entero:
// lo implementan Event y EventAccessSummary (una proyeccion de solo estas columnas, que usa
// ImageController para autorizar cada imagen).
public interface EventAccessData {

    Long getId();

    // null solo en una fila vieja que todavia no paso por el backfill de postgres-migrations.sql.
    Long getCreatorId();

    boolean isPrivate();

    boolean isApproved();

    // La persona es la organizadora del evento.
    default boolean isCreatedBy(final Long userId) {
        return userId != null && userId.equals(getCreatorId());
    }

    // Un admin puede aprobar/rechazar/moderar el evento salvo que sea suyo (conflicto de intereses: el
    // creador no puede ser tambien quien lo revisa). Es la regla de AdminSecurity.canModerateEvent().
    default boolean canBeModeratedBy(final Long adminId) {
        return !isCreatedBy(adminId);
    }
}
