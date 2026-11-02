// Por que alguien no fue a un evento ya pasado al que se habia inscripto (ver RegistrationEntry). Los nombres
// coinciden con las claves auth.registrations.pastStatus.* de los mensajes.
package ar.edu.itba.paw.services;

public enum NotAttendedReason {
    // El evento se cancelo (o lo dio de baja un admin): no sucedio.
    CANCELLED_EVENT,
    WAITLISTED,
    PENDING,
    REJECTED
}
