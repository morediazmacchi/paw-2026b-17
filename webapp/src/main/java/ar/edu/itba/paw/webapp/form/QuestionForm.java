package ar.edu.itba.paw.webapp.form;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

// Texto de una pregunta o de una respuesta (EventQuestionController.ask() / answer()).
public class QuestionForm {

    @NotBlank
    @Size(max = 300)
    private String text;

    public String getText() { return text; }
    public void setText(final String text) { this.text = text; }
}
