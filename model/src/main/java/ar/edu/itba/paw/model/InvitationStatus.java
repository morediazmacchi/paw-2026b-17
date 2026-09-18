// Los dos unicos estados de una invitacion de lista de espera.
//
// No hay "rechazada" ni "vencida" a proposito: en esos dos casos se borra la
// inscripcion entera y esta invitacion se va con ella (ON DELETE CASCADE), asi
// que la persona sale de la lista y puede volver a anotarse ultima si quiere.
// Tienen que coincidir con el CHECK de la columna status en waitlist_invitation.
package ar.edu.itba.paw.model;

public enum InvitationStatus {
    PENDING,
    ACCEPTED
}
