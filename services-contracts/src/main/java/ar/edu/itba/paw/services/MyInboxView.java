// La bandeja: los avisos y los grupos de chat, dos listas paginadas independientes. Lo devuelve
// MyAccountService.findInbox().
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.ChatGroup;

public class MyInboxView {

    private final InboxPage inbox;
    private final PageResult<ChatGroup> chatGroups;

    public MyInboxView(final InboxPage inbox, final PageResult<ChatGroup> chatGroups) {
        this.inbox = inbox;
        this.chatGroups = chatGroups;
    }

    // Una pagina de avisos con sus eventos.
    public InboxPage getInbox() { return inbox; }

    // Una pagina de los grupos de chat de los que es parte.
    public PageResult<ChatGroup> getChatGroups() { return chatGroups; }
}
