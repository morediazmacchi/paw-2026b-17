// Resultado de intentar cancelar un evento (EventController.cancelar()).
package ar.edu.itba.paw.services;

public enum EventCancelResult {
    CANCELLED,

    // No existe. Solo lo devuelve removeByAdmin(): cancel() (el del creador)
    // lanza EventNotFoundException en cambio. (Que quien pide sea el dueno lo
    // decide @PreAuthorize en el controller, no este metodo.)
    NOT_FOUND,

    // Ya estaba cancelado. Se distingue de CANCELLED para no volver a mandar
    // los mails de aviso si alguien clickea "Cancelar" dos veces (dos tabs,
    // doble click, etc.).
    ALREADY_CANCELLED,

    // El evento ya paso: no se cancela (a quienes fueron no se les puede avisar que "se cancelo" algo que sucedio).
    // Solo lo devuelve cancel() (el del creador): una baja por moderacion (removeByAdmin()) si puede alcanzar a uno ya
    // realizado.
    FINISHED
}
