// Implementa el contrato InboxMessageDao (modulo persistence-contracts) con JPA/Hibernate.
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.InboxMessage;
import ar.edu.itba.paw.model.User;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TemporalType;
import java.util.Date;
import java.util.Collections;
import java.util.List;

@Repository
public class InboxMessageJpaDao implements InboxMessageDao {

    @PersistenceContext
    private EntityManager em;

    @Override
    public InboxMessage create(final InboxMessage message) {
        em.persist(message);
        return message;
    }

    @Override
    public List<InboxMessage> findByRecipientUserId(final long userId, final int page, final int pageSize) {
        // Dos pasos (1+1): primero solo los ids de la pagina (cada fila es un numero, asi el
        // LIMIT/OFFSET sale siempre de la base), despues las entidades de esos ids.
        final List<Long> ids = em.createQuery(
                        "SELECT m.id FROM InboxMessage m WHERE m.user.id = :userId "
                                + "ORDER BY m.createdAt DESC, m.id DESC", Long.class)
                .setParameter("userId", userId)
                .setFirstResult((page - 1) * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return PagedIds.inOrder(ids,
                em.createQuery("FROM InboxMessage m WHERE m.id IN :ids", InboxMessage.class)
                        .setParameter("ids", ids)
                        .getResultList(),
                InboxMessage::getId);
    }

    @Override
    public long countByUserId(final long userId) {
        return em.createQuery("SELECT COUNT(m) FROM InboxMessage m WHERE m.user.id = :userId", Long.class)
                .setParameter("userId", userId)
                .getSingleResult();
    }

    @Override
    public long countUnreadByUserId(final long userId) {
        return em.createQuery(
                        "SELECT COUNT(m) FROM InboxMessage m WHERE m.user.id = :userId AND m.readAt IS NULL",
                        Long.class)
                .setParameter("userId", userId)
                .getSingleResult();
    }

    @Override
    public int markReadByIds(final long userId, final List<Long> ids, final Date when) {
        // UPDATE masivo (bulk JPQL): una sola sentencia, y devuelve cuantas filas cambio.
        // No actualiza las entidades que ya esten cargadas en el EntityManager.
        // "m.user.id = :userId": los ids vienen del formulario, asi que un id de un mensaje
        // ajeno no marca nada.
        return em.createQuery(
                        "UPDATE InboxMessage m SET m.readAt = :when "
                                + "WHERE m.id IN :ids AND m.user.id = :userId AND m.readAt IS NULL")
                .setParameter("when", when, TemporalType.TIMESTAMP)
                .setParameter("ids", ids)
                .setParameter("userId", userId)
                .executeUpdate();
    }

    @Override
    public int linkUserId(final String email, final long userId) {
        // Bulk JPQL: mismo criterio que EventInvitationJpaDao.linkUserId().
        return em.createQuery(
                        "UPDATE InboxMessage m SET m.user = :user WHERE m.recipientEmail = :email AND m.user IS NULL")
                .setParameter("user", em.getReference(User.class, userId))
                .setParameter("email", email)
                .executeUpdate();
    }
}
