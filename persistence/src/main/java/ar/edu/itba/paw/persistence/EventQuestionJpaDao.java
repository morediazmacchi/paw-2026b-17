// Implementa el contrato EventQuestionDao (modulo persistence-contracts) con JPA/Hibernate.
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.EventQuestion;
import ar.edu.itba.paw.model.User;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TemporalType;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public class EventQuestionJpaDao implements EventQuestionDao {

    @PersistenceContext
    private EntityManager em;

    @Override
    public EventQuestion create(final EventQuestion question) {
        // Ver RegistrationJpaDao.create(): de quien pregunta solo hace falta el id para la relacion.
        question.setUser(em.getReference(User.class, question.getUser().getId()));
        em.persist(question);
        return question;
    }

    @Override
    public Optional<EventQuestion> findById(final long id) {
        // JOIN FETCH: trae a quien pregunto en la misma consulta, asi el nombre y el mail
        // (getAskerName/getAskerEmail) se pueden leer despues, incluso fuera de la transaccion
        // (por ejemplo desde el hilo que manda el mail).
        return em.createQuery(
                        "SELECT q FROM EventQuestion q JOIN FETCH q.user WHERE q.id = :id", EventQuestion.class)
                .setParameter("id", id)
                .getResultList().stream().findFirst();
    }

    @Override
    public List<EventQuestion> findByEvent(final long eventId, final int page, final int pageSize) {
        // id como desempate, igual que en la bandeja.
        // JOIN FETCH q.user: cada pregunta viene con quien la hizo en la misma consulta (sin una
        // consulta extra por pregunta).
        return em.createQuery(
                        "SELECT q FROM EventQuestion q JOIN FETCH q.user WHERE q.event.id = :eventId "
                                + "ORDER BY q.createdAt DESC, q.id DESC", EventQuestion.class)
                .setParameter("eventId", eventId)
                .setFirstResult((page - 1) * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
    }

    @Override
    public long countByEvent(final long eventId) {
        return em.createQuery("SELECT COUNT(q) FROM EventQuestion q WHERE q.event.id = :eventId", Long.class)
                .setParameter("eventId", eventId)
                .getSingleResult();
    }

    @Override
    public Optional<Long> findEventCreatorId(final long questionId) {
        // Una sola columna (event.creator_id): ni la pregunta, ni el evento, ni el usuario.
        return em.createQuery("SELECT q.event.creator.id FROM EventQuestion q WHERE q.id = :id", Long.class)
                .setParameter("id", questionId)
                .getResultList().stream().findFirst();
    }

    @Override
    public boolean answer(final long id, final String answer, final Date when) {
        // Bulk JPQL a proposito (no em.find + setter): "WHERE answerText IS NULL" tiene que
        // ser atomico para que dos respuestas simultaneas no se pisen (gana una sola).
        final boolean answered = em.createQuery(
                        "UPDATE EventQuestion q SET q.answer = :answer, q.answeredAt = :when "
                                + "WHERE q.id = :id AND q.answer IS NULL")
                .setParameter("answer", answer)
                .setParameter("when", when, TemporalType.TIMESTAMP)
                .setParameter("id", id)
                .executeUpdate() == 1;
        if (answered) {
            // Un UPDATE masivo no actualiza las entidades que ya estan cargadas en esta
            // transaccion (el service la leyo con findById). Si no se la refresca, queda con
            // answeredAt = null y cualquier cambio posterior sobre ella hace que Hibernate
            // escriba ese null de vuelta en la base (dirty checking).
            final EventQuestion loaded = em.find(EventQuestion.class, id);
            if (loaded != null) {
                em.refresh(loaded);
            }
        }
        return answered;
    }

    @Override
    public boolean delete(final long id) {
        final EventQuestion question = em.find(EventQuestion.class, id);
        if (question == null) {
            return false;
        }
        em.remove(question);
        return true;
    }
}
