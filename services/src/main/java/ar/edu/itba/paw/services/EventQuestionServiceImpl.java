// Implementa el contrato EventQuestionService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventQuestion;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.EventQuestionDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class EventQuestionServiceImpl implements EventQuestionService {

    static final int PAGE_SIZE = 10;

    private final EventQuestionDao questionDao;
    private final EventService eventService;
    private final InboxService inboxService;
    private final NotificationService notificationService;

    @Autowired
    public EventQuestionServiceImpl(final EventQuestionDao questionDao, final EventService eventService,
                                    final InboxService inboxService,
                                    final NotificationService notificationService) {
        this.questionDao = questionDao;
        this.eventService = eventService;
        this.inboxService = inboxService;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public QuestionResult ask(final long eventId, final User asker, final String text) {
        final Event event = eventService.findById(eventId).orElse(null);
        if (event == null) {
            return QuestionResult.NOT_ALLOWED;
        }
        if (Long.valueOf(asker.getId()).equals(event.getCreatorId())) {
            return QuestionResult.OWN_EVENT;
        }
        if (event.isCancelled() || !event.getEventDate().after(new Date())) {
            return QuestionResult.CLOSED;
        }
        final EventQuestion question = questionDao.create(new EventQuestion(eventId, asker.getId(), text.trim()));
        question.setAskerName(asker.getName());
        question.setAskerEmail(asker.getEmail());

        inboxService.add(event.getCreatorEmail(), InboxMessageType.NEW_QUESTION, eventId,
                displayName(asker.getName(), asker.getEmail()), null);
        notificationService.notifyNewQuestion(event, question);
        return QuestionResult.CREATED;
    }

    @Override
    @Transactional
    public AnswerResult answer(final long eventId, final long questionId, final long answererId,
                               final String text) {
        final EventQuestion question = questionDao.findById(questionId).orElse(null);
        // Que la pregunta sea DE ESTE evento: si no, con un POST armado a mano se
        // podria responder una pregunta de otro evento desde la URL de uno propio.
        if (question == null || question.getEventId() != eventId) {
            return AnswerResult.NOT_ALLOWED;
        }
        final Event event = eventService.findById(eventId).orElse(null);
        if (event == null || !Long.valueOf(answererId).equals(event.getCreatorId())) {
            return AnswerResult.NOT_ALLOWED;
        }
        if (!questionDao.answer(questionId, text.trim(), new Date())) {
            return AnswerResult.ALREADY_ANSWERED;
        }
        question.setAnswer(text.trim());

        inboxService.add(question.getAskerEmail(), InboxMessageType.QUESTION_ANSWERED, eventId, null, null);
        notificationService.notifyQuestionAnswered(event, question);
        return AnswerResult.ANSWERED;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<EventQuestion> findPage(final long eventId, final int page) {
        final int safePage = Math.max(page, 1);
        return new PageResult<>(questionDao.findByEvent(eventId, safePage, PAGE_SIZE),
                questionDao.countByEvent(eventId), safePage, PAGE_SIZE);
    }

    @Override
    @Transactional
    public void deleteByAdmin(final long questionId) {
        questionDao.delete(questionId);
    }

    // Para la bandeja del organizador: el nombre, o el mail si no cargo nombre.
    private static String displayName(final String name, final String email) {
        return name == null || name.trim().isEmpty() ? email : name.trim();
    }
}
