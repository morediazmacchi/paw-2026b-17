// Resultado de intentar mandar un mensaje al grupo (EventChatController.send()).
package ar.edu.itba.paw.services;

public enum ChatSendResult {
    SENT,

    // Vacio, o mas largo que EventChatService.MAX_MESSAGE_LENGTH.
    INVALID,

    // El evento ya paso o se cancelo: el chat es de solo lectura.
    CLOSED,

    // No es miembro activo del grupo (nunca se unio, se dio de baja o lo sacaron).
    NOT_ALLOWED
}
