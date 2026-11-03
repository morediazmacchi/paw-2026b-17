package ar.edu.itba.paw.webapp.form;

import ar.edu.itba.paw.services.EventQuestionService;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

// Texto de una pregunta o de una respuesta (EventQuestionController.ask() / answer()).
public class QuestionForm {

    @NotBlank
    @Size(max = EventQuestionService.MAX_QUESTION_LENGTH)
    private String text;

    public String getText() { return text; }
    public void setText(final String text) { this.text = text; }
}
