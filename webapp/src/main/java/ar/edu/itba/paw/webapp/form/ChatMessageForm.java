package ar.edu.itba.paw.webapp.form;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

// Texto de un mensaje del grupo de chat (EventChatController.send()). El 1000 tiene
// que coincidir con EventChatService.MAX_MESSAGE_LENGTH y con la columna body.
public class ChatMessageForm {

    @NotBlank
    @Size(max = 1000)
    private String text;

    public String getText() { return text; }
    public void setText(final String text) { this.text = text; }
}
