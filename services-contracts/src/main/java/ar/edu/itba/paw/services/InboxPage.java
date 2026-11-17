// Una pagina de la bandeja: los avisos (cada uno con su evento ya cargado, message.getEvent(), si se refiere a uno).
// Lo devuelve InboxService.findPageWithEvents().
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.InboxMessage;

public class InboxPage {

    private final PageResult<InboxMessage> messages;

    public InboxPage(final PageResult<InboxMessage> messages) {
        this.messages = messages;
    }

    public PageResult<InboxMessage> getMessages() { return messages; }

    // Alguno de los avisos de ESTA pagina sigue sin leer (habilita "marcar como leidas").
    public boolean isHasUnread() {
        return messages.getContent().stream().anyMatch(InboxMessage::isUnread);
    }
}
