// Implementa el contrato AccountNotificationService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class AccountNotificationServiceImpl implements AccountNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountNotificationServiceImpl.class);

    // Fallback cuando la cuenta todavia no eligio idioma a mano
    private static final Locale DEFAULT_LOCALE = new Locale("es");

    private final MailService mailService;
    // Los links de los mails: el service pide cada uno por lo que es y no conoce rutas ni servidor.
    private final MailLinks links;

    @Autowired
    public AccountNotificationServiceImpl(final MailService mailService, final MailLinks links) {
        this.mailService = mailService;
        this.links = links;
    }

    @Async
    @Override
    public void notifyAccountVerification(final User user, final String token, final String redirectAfterConfirm) {
        try {
            final Locale locale = localeFor(user);
            final Map<String, Object> variables = new HashMap<>();
            variables.put("personName", displayName(user.getName(), user.getEmail()));
            // Si venia de algun lado (una invitacion a un evento privado), el link lo lleva: al confirmar y
            // loguearse vuelve ahi en vez de al home. Como viaja eso en la URL lo sabe MailLinks, no el service.
            variables.put("confirmUrl", links.confirmAccount(token, redirectAfterConfirm));

            mailService.send(user.getEmail(),
                    "account-verification",
                    "mail.accountVerification.subject",
                    null,
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el mail de confirmacion de cuenta, usuario {}", user.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyPasswordReset(final User user, final String token) {
        try {
            final Locale locale = localeFor(user);
            final Map<String, Object> variables = new HashMap<>();
            variables.put("personName", displayName(user.getName(), user.getEmail()));
            variables.put("resetUrl", links.resetPassword(token));

            mailService.send(user.getEmail(),
                    "password-reset",
                    "mail.passwordReset.subject",
                    null,
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el mail de restablecer contraseña, usuario {}", user.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyUserSuspended(final User user) {
        try {
            final Locale locale = localeFor(user);
            final Map<String, Object> variables = new HashMap<>();
            variables.put("personName", displayName(user.getName(), user.getEmail()));

            mailService.send(user.getEmail(),
                    "account-suspended",
                    "mail.accountSuspended.subject",
                    null,
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el aviso de cuenta suspendida, usuario {}", user.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyUserReactivated(final User user) {
        try {
            final Locale locale = localeFor(user);
            final Map<String, Object> variables = new HashMap<>();
            variables.put("personName", displayName(user.getName(), user.getEmail()));
            variables.put("loginUrl", links.login());

            mailService.send(user.getEmail(),
                    "account-reactivated",
                    "mail.accountReactivated.subject",
                    null,
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el aviso de cuenta reactivada, usuario {}", user.getId(), e);
        }
    }

    // El idioma guardado de la cuenta (que ya viene cargada), o el de por defecto si nunca eligio uno.
    private static Locale localeFor(final User user) {
        final String languageTag = user.getLocale();
        return (languageTag == null || languageTag.isEmpty()) ? DEFAULT_LOCALE : new Locale(languageTag);
    }

    // Con que nombre saludamos: el de la cuenta; si no tiene, lo que va antes del arroba (es mejor "Hola sol," que
    // "Hola null,").
    private static String displayName(final String name, final String email) {
        if (name != null && !name.trim().isEmpty()) {
            return name.trim();
        }
        if (email == null) {
            return "";
        }
        final int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
