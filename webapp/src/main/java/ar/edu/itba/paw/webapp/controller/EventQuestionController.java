package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.AnswerResult;
import ar.edu.itba.paw.services.EventQuestionService;
import ar.edu.itba.paw.services.QuestionResult;
import ar.edu.itba.paw.webapp.form.QuestionForm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.validation.Valid;

// Preguntas y respuestas sobre un evento (desde el detalle).
@Controller
public class EventQuestionController {

    private final EventQuestionService eventQuestionService;

    @Autowired
    public EventQuestionController(final EventQuestionService eventQuestionService) {
        this.eventQuestionService = eventQuestionService;
    }

    // Preguntar. Quien puede ver el evento lo decide @PreAuthorize antes de entrar (ver EventSecurity).
    @PreAuthorize("@eventSecurity.canView(#id, authentication)")
    @RequestMapping(value = "/eventos/{id}/preguntas", method = RequestMethod.POST)
    public ModelAndView ask(@PathVariable final long id, @Valid final QuestionForm form,
                            final BindingResult errors, @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                            final RedirectAttributes redirectAttributes) {
        if (errors.hasErrors()) {
            redirectAttributes.addFlashAttribute("questionMessage", "INVALID");
        } else {
            final QuestionResult result = eventQuestionService.ask(id, currentUser, form.getText());
            redirectAttributes.addFlashAttribute("questionMessage", result.name());
        }
        return new ModelAndView("redirect:/eventos/" + id + "#preguntas");
    }

    // Responder. Quien puede lo decide @PreAuthorize antes de entrar, por la pregunta: el organizador del
    // evento al que ella pertenece (ver EventSecurity.canAnswerQuestion). El id del evento de la URL es
    // solo ruteo (a donde se vuelve), no autoriza nada.
    @PreAuthorize("@eventSecurity.canAnswerQuestion(#questionId, authentication)")
    @RequestMapping(value = "/eventos/{id}/preguntas/{questionId}/responder", method = RequestMethod.POST)
    public ModelAndView answer(@PathVariable final long id, @PathVariable final long questionId,
                               @Valid final QuestionForm form, final BindingResult errors,
                               final RedirectAttributes redirectAttributes) {
        if (errors.hasErrors()) {
            redirectAttributes.addFlashAttribute("questionMessage", "INVALID");
        } else {
            final AnswerResult result = eventQuestionService.answer(questionId, form.getText());
            redirectAttributes.addFlashAttribute("questionMessage", result.name());
        }
        return new ModelAndView("redirect:/eventos/" + id + "#preguntas");
    }
}
