// Contrato de persistencia para Image. Implementacion: ImageJdbcDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.EventAccessSummary;
import ar.edu.itba.paw.model.Image;

import java.util.Optional;

public interface ImageDao {
    Image create(Image image);
    Optional<Image> findById(long id);

    // Los datos del evento del que cuelga la imagen que alcanzan para decidir quien puede verla
    // (ver EventAccessSummary), en una sola consulta de cuatro columnas: no se carga el Event.
    // Vacio si la imagen no cuelga de ningun evento: una imagen subida que nunca llego a asociarse.
    Optional<EventAccessSummary> findOwnerEventAccess(long imageId);
}
