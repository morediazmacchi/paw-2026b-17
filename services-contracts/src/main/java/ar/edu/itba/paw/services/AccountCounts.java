// Los numeritos del menu de la cuenta (inscripciones, favoritos, eventos creados e invitaciones).
// Lo devuelve AccountSummaryService.countsFor().
package ar.edu.itba.paw.services;

public class AccountCounts {

    private final long registrations;
    private final long favorites;
    private final long events;
    private final long invitations;

    public AccountCounts(final long registrations, final long favorites, final long events,
                         final long invitations) {
        this.registrations = registrations;
        this.favorites = favorites;
        this.events = events;
        this.invitations = invitations;
    }

    public long getRegistrations() { return registrations; }
    public long getFavorites() { return favorites; }
    public long getEvents() { return events; }
    public long getInvitations() { return invitations; }
}
