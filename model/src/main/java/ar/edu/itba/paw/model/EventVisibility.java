package ar.edu.itba.paw.model;

public enum EventVisibility {
    // No aparece en /eventos ni en las busquedas, y el detalle da 404 salvo
    // que seas el creador o ya tengas una inscripcion (ver EventController).
    PRIVATE,
    PUBLIC
}
