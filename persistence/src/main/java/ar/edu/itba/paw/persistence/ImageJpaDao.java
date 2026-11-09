// Implementa el contrato ImageDao (modulo persistence-contracts) con JPA/Hibernate.
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.EventAccessSummary;
import ar.edu.itba.paw.model.EventApprovalStatus;
import ar.edu.itba.paw.model.EventVisibility;
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
    public Image create(final byte[] data, final String contentType, final String fileName) {
        final Image image = new Image(data, contentType, fileName, data.length);
        em.persist(image);
        return image;
    }

    @Override
    public Optional<Image> findById(final long id) {
        return Optional.ofNullable(em.find(Image.class, id));
    }

    @Override
    public boolean delete(final long id) {
        final Image image = em.find(Image.class, id);
        if (image == null) {
            return false;
        }
        em.remove(image);
        return true;
    }

    @Override
    public Optional<EventAccessSummary> findOwnerEventAccess(final long imageId) {
        // Proyeccion: solo las cuatro columnas que necesita la regla de visibilidad, no el Event
        // (sin su fila entera, ni la subconsulta de la portada, ni las colecciones). Se une por la
        // coleccion imageIds (event_image); image_id es UNIQUE, asi que a lo sumo una fila.
        // e.creator.id no hace JOIN con users: es la propia columna creator_id del evento.
        final List<Object[]> rows = em.createQuery(
                        "SELECT e.id, e.creator.id, e.visibility, e.approvalStatus "
                                + "FROM Event e JOIN e.imageIds i WHERE i = :imageId", Object[].class)
                .setParameter("imageId", imageId)
                .getResultList();
        return rows.stream()
                .map(row -> new EventAccessSummary((Long) row[0], (Long) row[1],
                        row[2] == EventVisibility.PRIVATE, row[3] == EventApprovalStatus.APPROVED))
                .findFirst();
    }
}
