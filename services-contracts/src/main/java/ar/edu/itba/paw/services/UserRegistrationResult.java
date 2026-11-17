// Resultado de intentar registrar una cuenta propia (email + password).
package ar.edu.itba.paw.services;

public enum UserRegistrationResult {
    // El pedido se acepto: o se creo la cuenta (y se mando el mail de confirmacion), o ese mail YA tenia una cuenta
    // y se le aviso por mail a su dueno. A proposito es el mismo resultado: quien se registra no puede averiguar
    // si un mail esta registrado (la pagina responde igual en los dos casos).
    ACCEPTED,
    // La contraseña es mas corta que UserService.MIN_PASSWORD_LENGTH. El form ya lo valida para dar feedback.
    WEAK_PASSWORD
}
