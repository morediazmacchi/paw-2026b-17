package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.AnswerResult;
import ar.edu.itba.paw.services.EventQuestionService;
import ar.edu.itba.paw.services.EventService;
import ar.edu.itba.paw.services.QuestionResult;
import ar.edu.itba.paw.services.RegistrationService;
import ar.edu.itba.paw.webapp.exception.EventNotFoundException;
import ar.edu.itba.paw.webapp.form.QuestionForm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.validation.Valid;
import java.util.Optional;

// Preguntas y respuestas sobre un evento (desde el detalle).
@Controller
public class EventQuestionController {

    private final EventService eventService;
    private final RegistrationService registrationService;
    private final EventQuestionService eventQuestionService;
    private final EventAccessChecker accessChecker;

    @Autowired
    public EventQuestionController(final EventService eventService, final RegistrationService registrationService,
                                    final EventQuestionService eventQuestionService, final EventAccessChecker accessChecker) {
        this.eventService = eventService;
        this.registrationService = registrationService;
        this.eventQuestionService = eventQuestionService;
        this.accessChecker = accessChecker;
    }

    // Preguntar. No hace falta tocar WebAuthConfig: no esta en el permitAll, asi
    // que ya exige estar logueado. El resultado vuelve como flash attribute
    // "questionMessage" y detail.jsp elige el cartel con eso.
    @RequestMapping(value = "/eventos/{id}/preguntas", method = RequestMethod.POST)
    public ModelAndView ask(@PathVariable final long id, @Valid final QuestionForm form,
                            final BindingResult errors, @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                            final RedirectAttributes redirectAttributes) {
        final Event event = eventService.findById(id).orElseThrow(() -> new EventNotFoundException(id));
        // Mismo chequeo que el detalle: en un privado, solo quien puede verlo pregunta.
        final Optional<Registration> myRegistration =
                registrationService.findByEventAndUserId(id, currentUser.getId());
        if (!accessChecker.canView(event, currentUser, myRegistration)) {
            throw new EventNotFoundException(id);
        }
        if (errors.hasErrors()) {
            redirectAttributes.addFlashAttribute("questionMessage", "INVALID");
        } else {
            final QuestionResult result = eventQuestionService.ask(id, currentUser, form.getText());
            redirectAttributes.addFlashAttribute("questionMessage", result.name());
        }
        return new ModelAndView("redirect:/eventos/" + id + "#preguntas");
    }

    // Responder. Que quien responde sea el organizador lo chequea el service.
    @RequestMapping(value = "/eventos/{id}/preguntas/{questionId}/responder", method = RequestMethod.POST)
    public ModelAndView answer(@PathVariable final long id, @PathVariable final long questionId,
                               @Valid final QuestionForm form, final BindingResult errors,
                               @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                               final RedirectAttributes redirectAttributes) {
        if (errors.hasErrors()) {
            redirectAttributes.addFlashAttribute("questionMessage", "INVALID");
        } else {
            final AnswerResult result = eventQuestionService.answer(id, questionId, currentUser.getId(), form.getText());
            redirectAttributes.addFlashAttribute("questionMessage", result.name());
        }
        return new ModelAndView("redirect:/eventos/" + id + "#preguntas");
    }
}
