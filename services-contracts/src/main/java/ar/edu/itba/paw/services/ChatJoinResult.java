// Resultado de intentar unirse al grupo de chat de un evento (EventChatController.join()).
package ar.edu.itba.paw.services;

public enum ChatJoinResult {
    JOINED,

    // Ya estaba en el grupo: no es un error, la pantalla lo trata como exito.
    ALREADY_MEMBER,

    // El evento ya paso o se cancelo: el chat quedo de solo lectura, no se suman miembros.
    CLOSED,

    // El evento no existe o la persona no tiene una inscripcion CONFIRMED (incluye al
    // organizador, que no se anota a su propio evento).
    NOT_ALLOWED
}
