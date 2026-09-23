// Resultado de intentar editar un evento (EventController.editarSubmit()).
package ar.edu.itba.paw.services;

public enum EventUpdateResult {
    UPDATED,

    // No existe, o existe pero quien pregunta no es el creador -- no se
    // distingue cual de los dos paso (mismo criterio que EventController al
    // mostrar 404 en los dos casos para "ver inscriptos").
    NOT_FOUND,

    // El maxCapacity nuevo es menor a la cantidad de gente ya confirmada.
    CAPACITY_TOO_LOW,

    // El evento esta cancelado: ya no se puede editar (ni avisar cambios a nadie).
    CANCELLED
}
