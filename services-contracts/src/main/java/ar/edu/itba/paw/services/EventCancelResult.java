// Resultado de intentar cancelar un evento (EventController.cancelar()).
package ar.edu.itba.paw.services;

public enum EventCancelResult {
    CANCELLED,

    // No existe. Solo lo devuelve removeByAdmin(): cancel() (el del creador)
    // lanza EventNotFoundException en cambio, tanto si no existe como si
    // quien pide no es el dueno.
    NOT_FOUND,

    // Ya estaba cancelado. Se distingue de CANCELLED para no volver a mandar
    // los mails de aviso si alguien clickea "Cancelar" dos veces (dos tabs,
    // doble click, etc.).
    ALREADY_CANCELLED
}
