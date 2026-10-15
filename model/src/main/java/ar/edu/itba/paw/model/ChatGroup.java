package ar.edu.itba.paw.model;

import java.util.Date;

// Un grupo de chat de los que participa una persona, tal como se lista en la bandeja
// (un circulo por grupo). imageId es la portada del evento, o null si no tiene fotos.
public class ChatGroup {

    private final long eventId;
    private final String eventTitle;
    private final Long imageId;
    private final long unreadCount;
    private final Date lastMessageAt;

    public ChatGroup(final long eventId, final String eventTitle, final Long imageId,
                     final long unreadCount, final Date lastMessageAt) {
        this.eventId = eventId;
        this.eventTitle = eventTitle;
        this.imageId = imageId;
        this.unreadCount = unreadCount;
        this.lastMessageAt = lastMessageAt;
    }

    public long getEventId() { return eventId; }
    public String getEventTitle() { return eventTitle; }
    public Long getImageId() { return imageId; }
    public long getUnreadCount() { return unreadCount; }
    public Date getLastMessageAt() { return lastMessageAt; }
    public boolean isUnread() { return unreadCount > 0; }
}
