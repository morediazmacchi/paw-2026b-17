// Los cuatro estados posibles de una inscripcion. En la base se guardan con
// name() (en mayuscula) y se leen con valueOf(), igual que EventVisibility y
// Barrio. Tienen que coincidir con el CHECK de la columna status en la tabla
// registration (schema.sql y postgres-migrations.sql).
package ar.edu.itba.paw.model;

public enum RegistrationStatus {
    CONFIRMED,
    WAITLISTED,
    // Solo existe en eventos con requiresApproval=true: espera a que el creador la acepte o la rechace.
    PENDING,
    // Estado terminal: el creador la rechazo (ver RegistrationServiceImpl.reject()).
    REJECTED
}
