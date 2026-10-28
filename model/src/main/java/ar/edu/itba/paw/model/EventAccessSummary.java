package ar.edu.itba.paw.model;

// Proyeccion de un evento con solo lo que hace falta para decidir si alguien puede verlo (ver
// EventAccessData). No es una entidad: se arma con una consulta de cuatro columnas, sin cargar
// la fila entera del evento ni sus colecciones.
public class EventAccessSummary implements EventAccessData {

    private final Long id;
    private final Long creatorId;
    private final boolean isPrivate;
    private final boolean isApproved;

    public EventAccessSummary(final Long id, final Long creatorId, final boolean isPrivate,
                              final boolean isApproved) {
        this.id = id;
        this.creatorId = creatorId;
        this.isPrivate = isPrivate;
        this.isApproved = isApproved;
    }

    @Override
    public Long getId() { return id; }

    @Override
    public Long getCreatorId() { return creatorId; }

    @Override
    public boolean isPrivate() { return isPrivate; }

    @Override
    public boolean isApproved() { return isApproved; }
}
