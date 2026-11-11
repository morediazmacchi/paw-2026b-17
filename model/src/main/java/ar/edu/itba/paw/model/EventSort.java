// Lista blanca de campos de ordenamiento validos para el listado de eventos.
package ar.edu.itba.paw.model;

// Cada opcion es campo + sentido (de menor a mayor o al reves). El SQL real de
// cada una vive solo en EventJpaDao.SORT_COLUMNS, salvo RECOMENDADOS (ver RecommendationSql).
public enum EventSort {
    // Segun cuan bien coincide cada evento con el texto buscado (ver EventSearchCriteria.getText() y
    // TextSearchSql). No es una columna: es un puntaje que calcula la base en la misma consulta que pagina.
    // Solo tiene sentido con texto de busqueda; sin el, el service lo reemplaza por el orden por defecto.
    RELEVANCIA,
    // Segun cuanto se parece cada evento a los que la persona ya asistio (ver EventSimilarityService).
    // No es una columna: es un puntaje que calcula la base de datos en la misma consulta que pagina
    // (EventDao.findRecommendedPage()). Solo tiene sentido con EventSearchCriteria.viewerId y algun
    // evento asistido; sin eso el listado cae a FECHA_ASC.
    RECOMENDADOS,
    FECHA_ASC,
    FECHA_DESC,
    PRECIO_ASC,
    PRECIO_DESC,
    // Por cupos LIBRES (no por cupo maximo): lo que le sirve a quien busca.
    // Los eventos sin limite van al final de menor a mayor y al principio de
    // mayor a menor: tienen lugares "infinitos".
    CUPOS_ASC,
    CUPOS_DESC
}
