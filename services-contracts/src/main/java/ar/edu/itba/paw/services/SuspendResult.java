// Resultado de que un admin suspenda a otro usuario desde el panel (ver UserService.suspend(User, long)).
package ar.edu.itba.paw.services;

public enum SuspendResult {
    // Quedo suspendido (se avisa por mail y bandeja, y se cierran sus sesiones abiertas).
    OK,

    // Nadie se suspende a si mismo. Regla de negocio, no de acceso (quien puede suspender a quien,
    // segun el rol, lo decide AdminSecurity.canSuspend).
    OWN_ACCOUNT,

    // El usuario no existe.
    NOT_FOUND
}
