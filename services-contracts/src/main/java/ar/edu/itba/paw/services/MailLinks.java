// Los links que llevan los mails (NotificationService), con nombre por lo que SON, no por su ruta. El service pide
// "el link a mis inscripciones" y no sabe como se arma: el prefijo del servidor y las rutas de la web los conoce
// la implementacion, que vive en la capa web (webapp.mail.WebMailLinks).
package ar.edu.itba.paw.services;

public interface MailLinks {

    // "Mis inscripciones" de quien recibe el mail.
    String registrations();

    // El listado de eventos.
    String events();

    // El detalle de un evento.
    String event(long eventId);

    // El detalle de un evento, en la seccion de preguntas.
    String eventQuestions(long eventId);

    // La pantalla de editar un evento.
    String editEvent(long eventId);

    // Todos los inscriptos de un evento (pantalla del organizador).
    String attendees(long eventId);

    // Los pedidos pendientes de un evento con aprobacion, donde el organizador los acepta o rechaza.
    String pendingRequests(long eventId);

    // Confirmar que se quiere dejar de recibir avisos de esa busqueda guardada (pide iniciar sesion).
    String unsubscribeSavedSearch(long savedSearchId);

    // Iniciar sesion.
    String login();

    // Confirmar la cuenta con ese token. returnTo (puede ser null) es la pantalla a la que volver despues de
    // confirmar: el service lo recibe y lo pasa tal cual, sin saber como viaja en el link.
    String confirmAccount(String token, String returnTo);

    // Restablecer la contraseña con ese token.
    String resetPassword(String token);
}
