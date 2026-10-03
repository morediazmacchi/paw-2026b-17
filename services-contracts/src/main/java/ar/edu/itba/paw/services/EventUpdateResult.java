// Resultado de intentar editar un evento (EventController.editarSubmit()).
package ar.edu.itba.paw.services;

public enum EventUpdateResult {
    UPDATED,

    // El maxCapacity nuevo es menor a los lugares ya ocupados (confirmados,
    // invitaciones sin contestar y pedidos pendientes).
    CAPACITY_TOO_LOW,

    // El evento esta cancelado: ya no se puede editar (ni avisar cambios a nadie).
    CANCELLED
}
