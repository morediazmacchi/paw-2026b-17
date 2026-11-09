// Contrato de persistencia para Image. Implementacion: ImageJdbcDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.EventAccessSummary;
import ar.edu.itba.paw.model.Image;

import java.util.Optional;

public interface ImageDao {
    Image create(byte[] data, String contentType, String fileName);
    Optional<Image> findById(long id);

    // Borra la imagen. false si no existia.
    boolean delete(long id);

    // Los datos del evento del que cuelga la imagen que alcanzan para decidir quien puede verla
    // (ver EventAccessSummary), en una sola consulta de cuatro columnas: no se carga el Event.
    // Vacio si la imagen no cuelga de ningun evento: una imagen subida que nunca llego a asociarse.
    Optional<EventAccessSummary> findOwnerEventAccess(long imageId);
}
