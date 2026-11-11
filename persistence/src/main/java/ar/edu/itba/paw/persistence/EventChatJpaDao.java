// Implementa el contrato EventChatDao (modulo persistence-contracts) con JPA/Hibernate.
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.ChatGroup;
import ar.edu.itba.paw.model.ChatMember;
import ar.edu.itba.paw.model.ChatMessage;
import ar.edu.itba.paw.model.RegistrationStatus;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Repository
public class EventChatJpaDao implements EventChatDao {

    @PersistenceContext
    private EntityManager em;

    @Override
    public boolean addMember(final long registrationId, final long eventId, final long userId) {
        final Long existing = em.createQuery(
                        "SELECT COUNT(m) FROM ChatMember m WHERE m.registrationId = :registrationId "
                                + "OR (m.eventId = :eventId AND m.userId = :userId)", Long.class)
                .setParameter("registrationId", registrationId)
                .setParameter("eventId", eventId)
                .setParameter("userId", userId)
                .getSingleResult();
        if (existing > 0) {
            return false;
        }
        try {
            em.persist(new ChatMember(registrationId, eventId, userId));
            // flush: que el INSERT salga ahora, para enterarnos aca si otro "Unirme" casi
            // simultaneo se nos adelanto (el UNIQUE lo frena).
            em.flush();
            return true;
        } catch (final PersistenceException e) {
            return false;
        }
    }

    @Override
    public boolean removeMember(final long eventId, final long userId) {
        return em.createQuery("DELETE FROM ChatMember m WHERE m.eventId = :eventId AND m.userId = :userId")
                .setParameter("eventId", eventId)
                .setParameter("userId", userId)
                .executeUpdate() == 1;
    }

    @Override
    public boolean isMember(final long eventId, final long userId) {
        return em.createQuery(
                        "SELECT COUNT(m) FROM ChatMember m, Registration r WHERE r.id = m.registrationId "
                                + "AND m.eventId = :eventId AND m.userId = :userId AND r.status = :status",
                        Long.class)
                .setParameter("eventId", eventId)
                .setParameter("userId", userId)
                .setParameter("status", RegistrationStatus.CONFIRMED)
                .getSingleResult() > 0;
    }

    @Override
    public ChatMessage addMessage(final long eventId, final long userId, final String body) {
        final ChatMessage message = new ChatMessage(eventId, userId, body);
        em.persist(message);
        // flush + refresh: trae lo que pone la base (fecha) y el nombre de quien escribio.
        em.flush();
        em.refresh(message);
        return message;
    }

    @Override
    public List<ChatMessage> findLatest(final long eventId, final int limit) {
        // Se piden los ultimos N (id descendente) y se devuelven en orden de lectura.
        final List<ChatMessage> latest = new ArrayList<>(em.createQuery(
                        "SELECT m FROM ChatMessage m JOIN FETCH m.sender WHERE m.eventId = :eventId "
                                + "ORDER BY m.id DESC", ChatMessage.class)
                .setParameter("eventId", eventId)
                .setMaxResults(limit)
                .getResultList());
        Collections.reverse(latest);
        return latest;
    }

    @Override
    public List<ChatMessage> findAfter(final long eventId, final long afterId, final int limit) {
        return em.createQuery(
                        "SELECT m FROM ChatMessage m JOIN FETCH m.sender WHERE m.eventId = :eventId "
                                + "AND m.id > :afterId ORDER BY m.id ASC", ChatMessage.class)
                .setParameter("eventId", eventId)
                .setParameter("afterId", afterId)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    public void markRead(final long eventId, final long userId) {
        final Long lastId = em.createQuery("SELECT MAX(m.id) FROM ChatMessage m WHERE m.eventId = :eventId",
                        Long.class)
                .setParameter("eventId", eventId)
                .getSingleResult();
        em.createQuery("UPDATE ChatMember m SET m.lastReadId = :lastId "
                        + "WHERE m.eventId = :eventId AND m.userId = :userId")
                .setParameter("lastId", lastId == null ? 0L : lastId)
                .setParameter("eventId", eventId)
                .setParameter("userId", userId)
                .executeUpdate();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ChatGroup> findGroupsByUser(final long userId, final int page, final int pageSize) {
        // SQL nativo: junta portada, "sin leer" y ultimo mensaje en subconsultas, y devuelve
        // columnas sueltas (no una entidad), asi que JPQL no aporta nada aca. El CAST a TIMESTAMP
        // es porque Hibernate no sabe leer TIMESTAMP WITH TIME ZONE en una consulta nativa.
        final List<Object[]> rows = em.createNativeQuery(
                        "SELECT e.id AS event_id, e.title AS title, "
                                + "(SELECT ei.image_id FROM event_image ei WHERE ei.event_id = e.id "
                                + "ORDER BY ei.id LIMIT 1) AS image_id, "
                                + "(SELECT COUNT(*) FROM event_chat_message c WHERE c.event_id = m.event_id "
                                + "AND c.id > m.last_read_id AND c.user_id <> m.user_id) AS unread, "
                                + "(SELECT CAST(MAX(c.created_at) AS TIMESTAMP) FROM event_chat_message c "
                                + "WHERE c.event_id = m.event_id) AS last_message_at "
                                + "FROM event_chat_member m "
                                + "JOIN registration r ON r.id = m.registration_id AND r.status = 'CONFIRMED' "
                                + "JOIN event e ON e.id = m.event_id "
                                + "WHERE m.user_id = :userId "
                                + "ORDER BY last_message_at DESC NULLS LAST, m.joined_at DESC, m.event_id DESC")
                .setParameter("userId", userId)
                .setFirstResult((page - 1) * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
        final List<ChatGroup> groups = new ArrayList<>();
        for (final Object[] row : rows) {
            groups.add(new ChatGroup(((Number) row[0]).longValue(), (String) row[1],
                    row[2] == null ? null : ((Number) row[2]).longValue(),
                    ((Number) row[3]).longValue(), (Date) row[4]));
        }
        return groups;
    }

    @Override
    public long countGroupsByUser(final long userId) {
        // Mismas uniones y filtros que findGroupsByUser(): el total tiene que contar justo lo que se lista.
        return ((Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM event_chat_member m "
                                + "JOIN registration r ON r.id = m.registration_id AND r.status = 'CONFIRMED' "
                                + "JOIN event e ON e.id = m.event_id "
                                + "WHERE m.user_id = :userId")
                .setParameter("userId", userId)
                .getSingleResult()).longValue();
    }
}
