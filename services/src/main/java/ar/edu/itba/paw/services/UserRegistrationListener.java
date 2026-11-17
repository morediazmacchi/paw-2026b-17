package ar.edu.itba.paw.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// Reconciliacion de quien se registra (ver schema.sql): si esta persona ya venia con invitaciones o avisos de
// bandeja a su mail (invitada sin cuenta, ver EventInvitationService.invite() / InboxService.add()), ahora que tiene
// un id se los enlazamos. Se ejecuta en la transaccion de quien publica UserRegisteredEvent (listener comun, no
// @TransactionalEventListener): igual que cuando UserServiceImpl lo hacia a mano. No es un service: nadie lo
// inyecta, asi que no puede formar ciclos.
@Component
public class UserRegistrationListener {

    private final EventInvitationService eventInvitationService;
    private final InboxService inboxService;

    @Autowired
    public UserRegistrationListener(final EventInvitationService eventInvitationService,
                                    final InboxService inboxService) {
        this.eventInvitationService = eventInvitationService;
        this.inboxService = inboxService;
    }

    @EventListener
    public void onUserRegistered(final UserRegisteredEvent event) {
        eventInvitationService.linkAccount(event.getEmail(), event.getUserId());
        inboxService.linkAccount(event.getEmail(), event.getUserId());
    }
}
