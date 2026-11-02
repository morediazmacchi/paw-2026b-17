// Implementa el contrato AccountSummaryService (modulo services-contracts).
package ar.edu.itba.paw.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountSummaryServiceImpl implements AccountSummaryService {

    private final RegistrationService registrationService;
    private final FavoriteService favoriteService;
    private final EventService eventService;
    private final EventInvitationService eventInvitationService;

    @Autowired
    public AccountSummaryServiceImpl(final RegistrationService registrationService,
                                     final FavoriteService favoriteService, final EventService eventService,
                                     final EventInvitationService eventInvitationService) {
        this.registrationService = registrationService;
        this.favoriteService = favoriteService;
        this.eventService = eventService;
        this.eventInvitationService = eventInvitationService;
    }

    @Override
    @Transactional(readOnly = true)
    public AccountCounts countsFor(final long userId, final boolean isAdmin) {
        return new AccountCounts(registrationService.countByUserId(userId),
                favoriteService.countViewable(userId, isAdmin),
                eventService.countByCreatorId(userId),
                eventInvitationService.countByUser(userId));
    }
}
