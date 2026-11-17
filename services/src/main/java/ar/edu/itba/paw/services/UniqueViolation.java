package ar.edu.itba.paw.services;

import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

/**
 * Para los services que traducen la violacion de un UNIQUE (dos pedidos simultaneos que pasaron los dos el chequeo)
 * a un resultado, en vez de dejar escapar la excepcion. Solo ese error es "ya estaba": cualquier otra violacion
 * (un CHECK, un largo, una clave foranea) es un error de verdad y no se disfraza.
 */
final class UniqueViolation {

    // SQLSTATE 23505 = unique_violation (Postgres y HSQLDB).
    private static final String UNIQUE_VIOLATION = "23505";

    private UniqueViolation() {
    }

    static boolean isUniqueViolation(final DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException && UNIQUE_VIOLATION.equals(((SQLException) cause).getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
