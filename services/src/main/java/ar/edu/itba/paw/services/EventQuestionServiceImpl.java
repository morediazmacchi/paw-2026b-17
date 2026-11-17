// Implementa el contrato EventQuestionService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventQuestion;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.exception.EventNotFoundException;
import ar.edu.itba.paw.model.exception.UserNotFoundException;
import ar.edu.itba.paw.persistence.EventQuestionDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;

@Service
public class EventQuestionServiceImpl implements EventQuestionService {

    static final int PAGE_SIZE = 10;

    private final EventQuestionDao questionDao;
    private final EventService eventService;
    private final InboxService inboxService;
    private final NotificationService notificationService;
    private final UserService userService;

    @Autowired
    public EventQuestionServiceImpl(final EventQuestionDao questionDao, final EventService eventService,
                                    final InboxService inboxService,
                                    final NotificationService notificationService,
                                    final UserService userService) {
        this.questionDao = questionDao;
        this.eventService = eventService;
        this.inboxService = inboxService;
        this.notificationService = notificationService;
        this.userService = userService;
    }

    @Override
    @Transactional
    public QuestionResult ask(final long eventId, final long askerId, final String text) {
        // Evento inexistente: el 404 ya lo da @PreAuthorize en el controller antes de llegar aca; esto solo
        // se alcanza si desaparece justo en el medio (carrera). Es la excepcion de siempre (ErrorAdvice
        // la mapea a 404), no un resultado: los resultados son para conflictos de negocio.
        final Event event = eventService.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
        if (!event.isOpenForQuestions()) {
            return QuestionResult.CLOSED;
        }
        // El form ya lo valida para dar feedback; esto es la defensa del service (como el chat con su largo).
        final String body = text == null ? "" : text.trim();
        if (body.isEmpty() || body.length() > MAX_QUESTION_LENGTH) {
            return QuestionResult.INVALID;
        }
        // El nombre y el mail salen de la cuenta, no de la sesion (que solo lleva lo que hace falta para autorizar).
        final User asker = userService.findById(askerId).orElseThrow(() -> new UserNotFoundException(askerId));
        final EventQuestion question = questionDao.create(eventId, askerId, body);

        inboxService.add(event.getCreatorEmail(), InboxMessageType.NEW_QUESTION, eventId,
                displayName(asker.getName(), asker.getEmail()), null);
        notificationService.notifyNewQuestion(event, question, asker.getName(), asker.getEmail());
        return QuestionResult.CREATED;
    }

    @Override
    @Transactional
    public AnswerResult answer(final long questionId, final String text) {
        // Que quien responde sea el organizador del evento de la pregunta lo decide @PreAuthorize en
        // el controller (ver EventSecurity.canAnswerQuestion). El evento es el de la pregunta.
        // El form ya lo valida para dar feedback; esto es la defensa del service (igual que ask()), antes de
        // leer o escribir nada. Mismo largo que la pregunta: answer_text tambien es VARCHAR(300).
        final String body = text == null ? "" : text.trim();
        if (body.isEmpty() || body.length() > MAX_QUESTION_LENGTH) {
            return AnswerResult.INVALID;
        }
        final EventQuestion question = questionDao.findById(questionId).orElse(null);
        if (question == null) {
            return AnswerResult.NOT_FOUND;
        }
        final long eventId = question.getEventId();
        final Event event = eventService.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
        if (!questionDao.answer(questionId, body, new Date())) {
            return AnswerResult.ALREADY_ANSWERED;
        }
        question.setAnswer(body);

        // El nombre y el mail se leen ahora, con la pregunta en sesion (viene con JOIN FETCH), y se
        // pasan al envio @Async: no debe leer la relacion desde otro hilo.
        final String askerName = question.getAskerName();
        final String askerEmail = question.getAskerEmail();
        inboxService.add(askerEmail, InboxMessageType.QUESTION_ANSWERED, eventId, null, null);
        notificationService.notifyQuestionAnswered(event, question, askerName, askerEmail);
        return AnswerResult.ANSWERED;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> findEventId(final long questionId) {
        return questionDao.findEventId(questionId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<EventQuestion> findPage(final long eventId, final int page) {
        final long totalElements = questionDao.countByEvent(eventId);
        final int effectivePage = PageResult.clampPage(page, totalElements, PAGE_SIZE);
        return new PageResult<>(questionDao.findByEvent(eventId, effectivePage, PAGE_SIZE),
                totalElements, effectivePage, PAGE_SIZE);
    }

    @Override
    @Transactional
    public Optional<Long> deleteByAdmin(final long questionId) {
        final Optional<Long> eventId = questionDao.findById(questionId).map(EventQuestion::getEventId);
        if (eventId.isPresent()) {
            questionDao.delete(questionId);
        }
        return eventId;
    }

    // Para la bandeja del organizador: el nombre, o el mail si no cargo nombre.
    private static String displayName(final String name, final String email) {
        return name == null || name.trim().isEmpty() ? email : name.trim();
    }
}
