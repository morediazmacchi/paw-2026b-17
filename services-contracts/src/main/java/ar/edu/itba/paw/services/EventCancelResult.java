// Resultado de intentar cancelar un evento (EventController.cancelar()).
package ar.edu.itba.paw.services;

public enum EventCancelResult {
    CANCELLED,

    // No existe, o existe pero quien pregunta no es el creador -- mismo
    // criterio que EventUpdateResult.NOT_FOUND: no se distingue cual de los
    // dos paso.
    NOT_FOUND,

    // Ya estaba cancelado. Se distingue de CANCELLED para no volver a mandar
    // los mails de aviso si alguien clickea "Cancelar" dos veces (dos tabs,
    // doble click, etc.).
    ALREADY_CANCELLED
}
