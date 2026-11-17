// Los cuatro estados posibles de una inscripcion. En la base se guardan con
// name() (en mayuscula) y se leen con valueOf(), igual que EventVisibility y
// Barrio. Tienen que coincidir con el CHECK de la columna status en la tabla
// registration (schema.sql y postgres-migrations.sql).
package ar.edu.itba.paw.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum RegistrationStatus {
    CONFIRMED,
    WAITLISTED,
    // Solo existe en eventos con requiresApproval=true: espera a que el creador la acepte o la rechace.
    PENDING,
    // Estado terminal: el creador la rechazo (ver RegistrationServiceImpl.reject()).
    REJECTED;

    // Tiene el lugar confirmado (no esta pendiente, ni en espera, ni rechazada). Es la UNICA definicion de "tiene
    // el lugar": la usan el chat, las reseñas, revocar y las pantallas.
    public boolean isConfirmed() {
        return this == CONFIRMED;
    }

    // Los estados que cumplen isConfirmed(), para filtrar en SQL con la MISMA regla (ver
    // RegistrationDao.findConfirmedByEventId e InboxService.addForRegistrants): no se repite la lista a mano.
    public static Set<RegistrationStatus> withConfirmedPlace() {
        final Set<RegistrationStatus> result = EnumSet.noneOf(RegistrationStatus.class);
        for (final RegistrationStatus status : values()) {
            if (status.isConfirmed()) {
                result.add(status);
            }
        }
        return Collections.unmodifiableSet(result);
    }
}
