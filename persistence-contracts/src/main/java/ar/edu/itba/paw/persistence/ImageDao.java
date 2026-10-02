// Contrato de persistencia para Image. Implementacion: ImageJdbcDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Image;

import java.util.Optional;

public interface ImageDao {
    Image create(Image image);
    Optional<Image> findById(long id);

    // Id del evento del que cuelga la imagen (tabla event_image). Vacio si no
    // cuelga de ninguno: una imagen subida que nunca llego a asociarse.
    Optional<Long> findOwnerEventId(long imageId);
}
