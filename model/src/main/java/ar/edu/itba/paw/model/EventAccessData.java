package ar.edu.itba.paw.model;

// Los datos de un evento que alcanzan para decidir quien puede verlo (ver
// EventAccessService.canView). Existe para poder decidirlo sin cargar el Event entero:
// lo implementan Event y EventAccessSummary (una proyeccion de solo estas columnas, que usa
// ImageController para autorizar cada imagen).
public interface EventAccessData extends HasCreator {

    Long getId();

    boolean isPrivate();

    boolean isApproved();
}
