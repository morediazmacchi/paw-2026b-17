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
}
