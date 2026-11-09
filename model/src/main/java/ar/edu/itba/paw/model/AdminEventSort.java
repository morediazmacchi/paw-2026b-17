// Lista blanca de ordenes del panel de admin "Eventos". El SQL de cada uno vive solo en EventJpaDao.
package ar.edu.itba.paw.model;

public enum AdminEventSort {
    // Por fecha de creacion, el que se publico hace menos primero. Es el orden por defecto.
    CREATED_DESC,
    CREATED_ASC,
    // Lo que necesita una decision del admin (pendientes de aprobacion) va arriba; dentro de cada grupo, lo mas
    // reciente primero.
    PENDING_FIRST,
    // Por la fecha en la que se hace el evento.
    EVENT_DATE_DESC,
    EVENT_DATE_ASC,
    TITLE_ASC
}
