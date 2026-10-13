// Implementa el contrato ImageDao (modulo persistence-contracts) con JPA/Hibernate.
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Image;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;

@Repository
public class ImageJpaDao implements ImageDao {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Image create(final Image image) {
        em.persist(image);
        return image;
    }

    @Override
    public Optional<Image> findById(final long id) {
        return Optional.ofNullable(em.find(Image.class, id));
    }

    @Override
    public Optional<Long> findOwnerEventId(final long imageId) {
        // Nativa: event_image todavia no es una entidad (la tabla es del modelo de Event).
        // image_id es UNIQUE: a lo sumo una fila por imagen.
        final List<?> rows = em.createNativeQuery("SELECT event_id FROM event_image WHERE image_id = :imageId")
                .setParameter("imageId", imageId)
                .getResultList();
        return rows.stream().map(id -> ((Number) id).longValue()).findFirst();
    }
}
