// Implementa el contrato InboxMessageDao (modulo persistence-contracts) con JPA/Hibernate.
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.InboxMessage;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.model.User;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TemporalType;
import java.util.Collection;
import java.util.Date;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class InboxMessageJpaDao implements InboxMessageDao {

    @PersistenceContext
    private EntityManager em;

    @Override
    public InboxMessage create(final String recipientEmail, final InboxMessageType type, final Long eventId,
                               final String actorName, final String body) {
        // El aviso se relaciona con el evento por referencia (no hace falta cargarlo).
        final InboxMessage message = new InboxMessage(recipientEmail, type,
                eventId == null ? null : em.getReference(Event.class, eventId), actorName, body);
        // Optimista (ver schema.sql): si el destinatario ya tiene cuenta, se enlaza de una. Si no, queda en null y
        // se completa solo cuando se registre (ver linkUserId()).
        em.createQuery("SELECT u.id FROM User u WHERE u.email = :email", Long.class)
                .setParameter("email", recipientEmail)
                .getResultList().stream().findFirst()
                .ifPresent(userId -> message.setUser(em.getReference(User.class, userId)));
        em.persist(message);
        return message;
    }

    // Los dos INSERT ... SELECT de abajo son SQL nativo: JPQL no tiene un INSERT estandar. Corren igual en
    // Postgres y HSQLDB. No mencionan id ni created_at: los pone la base (SERIAL y DEFAULT now()), igual que
    // create(). El CAST del tipo es para HSQLDB, que no acepta un parametro sin tipo en la lista del SELECT.
    // recipient_email va en minuscula y sin espacios, y user_id es la cuenta con ese mail (LEFT JOIN: puede no
    // haber), lo mismo que hacia InboxServiceImpl.add() con cada uno por separado.

    @Override
    public int createForRegistrants(final long eventId, final InboxMessageType type,
                                    final Collection<RegistrationStatus> statuses) {
        return em.createNativeQuery(
                        "INSERT INTO inbox_message (recipient_email, user_id, type, event_id) "
                                + "SELECT LOWER(TRIM(r.email)), u.id, CAST(:type AS VARCHAR(40)), r.event_id "
                                + "FROM registration r "
                                + "LEFT JOIN users u ON u.email = LOWER(TRIM(r.email)) "
                                + "WHERE r.event_id = :eventId AND r.status IN (:statuses)")
                .setParameter("type", type.name())
                .setParameter("eventId", eventId)
                .setParameter("statuses", statuses.stream().map(Enum::name).collect(Collectors.toList()))
                .executeUpdate();
    }

    @Override
    public int createForRegistrations(final InboxMessageType type, final Collection<Long> registrationIds) {
        return em.createNativeQuery(
                        "INSERT INTO inbox_message (recipient_email, user_id, type, event_id) "
                                + "SELECT LOWER(TRIM(r.email)), u.id, CAST(:type AS VARCHAR(40)), r.event_id "
                                + "FROM registration r "
                                + "LEFT JOIN users u ON u.email = LOWER(TRIM(r.email)) "
                                + "WHERE r.id IN (:ids)")
                .setParameter("type", type.name())
                .setParameter("ids", registrationIds)
                .executeUpdate();
    }

    @Override
    public int createForOrganizerOfRegistrations(final InboxMessageType type, final Collection<Long> registrationIds) {
        // El actor replica Registration.getDisplayName(): el nombre tal cual si hay, el mail si es null o vacio.
        return em.createNativeQuery(
                        "INSERT INTO inbox_message (recipient_email, user_id, type, event_id, actor_name) "
                                + "SELECT LOWER(TRIM(e.creator_email)), u.id, CAST(:type AS VARCHAR(40)), r.event_id, "
                                + "CASE WHEN r.name IS NULL OR TRIM(r.name) = '' THEN r.email ELSE r.name END "
                                + "FROM registration r "
                                + "JOIN event e ON e.id = r.event_id "
                                + "LEFT JOIN users u ON u.email = LOWER(TRIM(e.creator_email)) "
                                + "WHERE r.id IN (:ids)")
                .setParameter("type", type.name())
                .setParameter("ids", registrationIds)
                .executeUpdate();
    }

    @Override
    public int createForFollowersOfCreator(final long eventId, final InboxMessageType type) {
        // El actor (o.name) sale de la cuenta del organizador en la misma consulta: no hace falta pasarlo (y un
        // parametro null en la lista del SELECT no tiene tipo para la base).
        return em.createNativeQuery(
                        "INSERT INTO inbox_message (recipient_email, user_id, type, event_id, actor_name) "
                                + "SELECT LOWER(TRIM(u.email)), u.id, CAST(:type AS VARCHAR(40)), e.id, o.name "
                                + "FROM event e "
                                + "JOIN users o ON o.id = e.creator_id "
                                + "JOIN follow f ON f.followed_id = e.creator_id "
                                + "JOIN users u ON u.id = f.follower_id "
                                + "WHERE e.id = :eventId")
                .setParameter("type", type.name())
                .setParameter("eventId", eventId)
                .executeUpdate();
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
        // Paso 2: las entidades de esos ids, con el MISMO ORDER BY que la consulta de arriba (el IN no ordena nada).
        // LEFT JOIN FETCH del evento: la bandeja muestra a que evento se refiere cada aviso (no todos tienen uno).
        return em.createQuery("SELECT m FROM InboxMessage m LEFT JOIN FETCH m.event WHERE m.id IN :ids "
                                + "ORDER BY m.createdAt DESC, m.id DESC", InboxMessage.class)
                .setParameter("ids", ids)
                .getResultList();
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
