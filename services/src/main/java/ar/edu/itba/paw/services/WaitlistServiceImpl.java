// Implementa el contrato WaitlistService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.model.WaitlistInvitation;
import ar.edu.itba.paw.persistence.EventDao;
import ar.edu.itba.paw.persistence.RegistrationDao;
import ar.edu.itba.paw.persistence.WaitlistInvitationDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class WaitlistServiceImpl implements WaitlistService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WaitlistServiceImpl.class);

    /** Cuanto tiempo tiene la persona para contestar. */
    private static final long INVITE_WINDOW_MILLIS = 24L * 60 * 60 * 1000;

    /**
     * 32 bytes de azar criptografico -> 43 caracteres. NO Math.random() ni un id
     * secuencial: el token es la unica credencial de quien abre el link, asi que
     * tiene que ser imposible de adivinar.
     */
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final WaitlistInvitationDao invitationDao;
    private final RegistrationDao registrationDao;
    private final EventDao eventDao;
    private final NotificationService notificationService;

    @Autowired
    public WaitlistServiceImpl(final WaitlistInvitationDao invitationDao,
                               final RegistrationDao registrationDao,
                               final EventDao eventDao,
                               final NotificationService notificationService) {
        this.invitationDao = invitationDao;
        this.registrationDao = registrationDao;
        this.eventDao = eventDao;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public int reconcile(final long eventId) {
        // Un unico "ahora" para toda la pasada: si se leyera el reloj en cada
        // paso, una invitacion podria vencerse en el medio del calculo.
        final Date now = new Date();

        // FOR UPDATE: bloquea la fila del evento. Dos cancelaciones simultaneas
        // se serializan aca y no le ofrecen el mismo lugar a dos personas.
        final Optional<Event> locked = eventDao.findByIdForUpdate(eventId);
        if (!locked.isPresent()) {
            return 0;
        }
        return reconcileLocked(locked.get(), now);
    }

    /**
     * El evento ya viene bloqueado por quien llama. Separado para poder
     * reusarlo desde accept() y decline(), que ya lo lockearon.
     */
    private int reconcileLocked(final Event event, final Date now) {
        // Sin cupo maximo nunca hay lista de espera: todos entran confirmados.
        if (event.getMaxCapacity() == null) {
            return 0;
        }
        // Un evento que ya paso no tiene lugares que ofrecer.
        if (event.getEventDate() != null && event.getEventDate().before(now)) {
            return 0;
        }
        // Un evento cancelado tampoco: no hay que invitar a nadie a algo que no va a pasar.
        if (event.isCancelled()) {
            return 0;
        }

        expirePendingInvitations(event.getId(), now);

        // EL CALCULO QUE SOSTIENE TODO: un cupo puede estar ocupado (alguien
        // confirmado) o RESERVADO (alguien con una invitacion pendiente, que
        // todavia esta decidiendo). Los dos se restan. Si solo contaramos los
        // confirmados, mientras la persona invitada piensa, otra entraria por
        // la puerta y terminariamos con mas gente que lugares.
        final long confirmed = registrationDao.countConfirmed(event.getId());
        final long reserved = invitationDao.countPending(event.getId());
        final int free = (int) (event.getMaxCapacity() - confirmed - reserved);
        if (free <= 0) {
            return 0;
        }

        final List<Registration> next = registrationDao.findNextWaitlisted(event.getId(), free);
        for (final Registration registration : next) {
            invite(event, registration, now);
        }

        // Si quedaron cupos sin ofrecer es porque no hay mas nadie esperando:
        // esos quedan libres y los agarra quien se anote desde la pagina. No
        // hace falta hacer nada, availableSpots() ya los va a ver libres.
        if (next.size() < free) {
            LOGGER.debug("Evento {}: quedan {} cupos abiertos al publico", event.getId(), free - next.size());
        }
        return next.size();
    }

    /**
     * A quien no contesto a tiempo se le borra la inscripcion: sale de la lista
     * y, si quiere, puede volver a anotarse ultimo. La invitacion se va con ella
     * por el ON DELETE CASCADE.
     */
    private void expirePendingInvitations(final long eventId, final Date now) {
        for (final WaitlistInvitation expired : invitationDao.findExpired(eventId, now)) {
            LOGGER.info("Vencio la invitacion {} del evento {}", expired.getId(), eventId);
            registrationDao.delete(expired.getRegistrationId());
        }
    }

    private void invite(final Event event, final Registration registration, final Date now) {
        // La columna token sigue siendo NOT NULL en la base, pero ya no viaja en
        // el mail: a quien entra lo identifica su sesion (ver findInvitation).
        final String token = newToken();
        final Date expiresAt = earliest(new Date(now.getTime() + INVITE_WINDOW_MILLIS), event.getEventDate());

        invitationDao.create(new WaitlistInvitation(registration.getId(), token, expiresAt));
        notificationService.notifyWaitlistInvitation(event, registration, expiresAt);
        LOGGER.info("Invitacion enviada a la inscripcion {} del evento {}", registration.getId(), event.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findEventsNeedingReconcile() {
        return registrationDao.findEventIdsWithWaitlist(new Date());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InvitationDetail> findForUser(final long eventId, final String email) {
        return findInvitation(eventId, email)
                .flatMap(invitation -> registrationDao.findById(invitation.getRegistrationId())
                        .flatMap(registration -> eventDao.findById(registration.getEventId())
                                .map(event -> new InvitationDetail(invitation, event))));
    }

    @Override
    @Transactional(readOnly = true)
    public long countReserved(final long eventId) {
        return invitationDao.countPending(eventId);
    }

    @Override
    @Transactional
    public InvitationResult accept(final long eventId, final String email) {
        final Date now = new Date();
        final WaitlistInvitation invitation = findInvitation(eventId, email).orElse(null);
        if (invitation == null) {
            return InvitationResult.NOT_FOUND;
        }
        if (!invitation.isPending()) {
            // Ya la habia aceptado: apretar dos veces el boton no es un error.
            return InvitationResult.ALREADY_USED;
        }

        final Registration registration = registrationDao.findById(invitation.getRegistrationId()).orElse(null);
        if (registration == null) {
            return InvitationResult.NOT_FOUND;
        }
        final Event event = eventDao.findByIdForUpdate(registration.getEventId()).orElse(null);
        if (event == null) {
            return InvitationResult.NOT_FOUND;
        }
        if (event.getEventDate() != null && event.getEventDate().before(now)) {
            return InvitationResult.EVENT_PASSED;
        }
        // Lo invitaron antes de que se cancelara y contesta despues: no hay lugar que confirmar.
        if (event.isCancelled()) {
            return InvitationResult.CANCELLED;
        }

        if (invitation.isExpiredAt(now)) {
            // Se le paso la hora justo antes de apretar. Queda afuera igual, y
            // el cupo se le ofrece al siguiente en la misma transaccion.
            registrationDao.delete(registration.getId());
            reconcileLocked(event, now);
            return InvitationResult.EXPIRED;
        }

        invitationDao.markAccepted(invitation.getId());
        registrationDao.updateStatus(registration.getId(), RegistrationStatus.CONFIRMED);
        registration.setStatus(RegistrationStatus.CONFIRMED);

        // Mismo mail que cuando alguien se anota normalmente: para la persona el
        // resultado es el mismo, tiene su lugar.
        notificationService.notifyRegistrationConfirmed(event, registration);
        notificationService.notifyNewRegistration(event, registration);
        return InvitationResult.ACCEPTED;
    }

    @Override
    @Transactional
    public InvitationResult decline(final long eventId, final String email) {
        final Date now = new Date();
        final WaitlistInvitation invitation = findInvitation(eventId, email).orElse(null);
        if (invitation == null) {
            return InvitationResult.NOT_FOUND;
        }
        if (!invitation.isPending()) {
            return InvitationResult.ALREADY_USED;
        }

        final Registration registration = registrationDao.findById(invitation.getRegistrationId()).orElse(null);
        if (registration == null) {
            return InvitationResult.NOT_FOUND;
        }
        final Event event = eventDao.findByIdForUpdate(registration.getEventId()).orElse(null);

        registrationDao.delete(registration.getId());
        if (event != null) {
            // El cupo que acaba de soltar se le ofrece al siguiente enseguida.
            reconcileLocked(event, now);
        }
        return InvitationResult.DECLINED;
    }

    // La invitacion de ESTA persona para ESTE evento. Quien es lo dice la sesion
    // (el email del usuario logueado), no el link: si el mail se reenvia, el otro
    // entra con su cuenta, no tiene inscripcion ahi y no encuentra nada.
    private Optional<WaitlistInvitation> findInvitation(final long eventId, final String email) {
        return registrationDao.findByEventAndEmail(eventId, email.trim().toLowerCase())
                .flatMap(registration -> invitationDao.findByRegistrationId(registration.getId()));
    }

    private static String newToken() {
        final byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        // Sin padding y en el alfabeto de URLs: entra tal cual en un link.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Darle 24hs para contestar una invitacion a un evento que es esta noche no
     * sirve de nada: la invitacion venceria despues del evento y ese cupo se
     * perderia. Se corta cuando empieza el evento.
     */
    private static Date earliest(final Date deadline, final Date eventDate) {
        if (eventDate == null || deadline.before(eventDate)) {
            return deadline;
        }
        return eventDate;
    }
}
