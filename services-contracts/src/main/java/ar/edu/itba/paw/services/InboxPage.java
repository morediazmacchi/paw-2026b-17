// Una pagina de la bandeja con los eventos a los que se refieren los avisos, por id. Lo devuelve
// InboxService.findPageWithEvents().
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.InboxMessage;

import java.util.Map;

public class InboxPage {

    private final PageResult<InboxMessage> messages;
    private final Map<Long, Event> eventsById;

    public InboxPage(final PageResult<InboxMessage> messages, final Map<Long, Event> eventsById) {
        this.messages = messages;
        this.eventsById = eventsById;
    }

    public PageResult<InboxMessage> getMessages() { return messages; }
    public Map<Long, Event> getEventsById() { return eventsById; }
}
