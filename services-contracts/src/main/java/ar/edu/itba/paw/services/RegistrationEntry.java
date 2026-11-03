// Una fila de "Mis inscripciones": la inscripcion, su evento y la reseña propia de ese evento (null si
// todavia no la dejo, o si el evento no paso). Forma parte de MyRegistrationsView.
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.Review;

public class RegistrationEntry {

    private final Registration registration;
    private final Event event;
    private final Review myReview;
    private final boolean canReview;
    private final NotAttendedReason notAttendedReason;
    private final boolean canCancel;

    public RegistrationEntry(final Registration registration, final Event event, final Review myReview,
                             final boolean canReview, final NotAttendedReason notAttendedReason, final boolean canCancel) {
        this.registration = registration;
        this.event = event;
        this.myReview = myReview;
        this.canReview = canReview;
        this.notAttendedReason = notAttendedReason;
        this.canCancel = canCancel;
    }

    public Registration getRegistration() { return registration; }
    public Event getEvent() { return event; }
    public Review getMyReview() { return myReview; }

    // Puede dejar su reseña ahora: el evento ya paso y no se cancelo, tuvo su lugar confirmado y todavia no la
    // dejo. Es la regla de ReviewService.create(), ya decidida (false en las inscripciones a eventos que vienen).
    public boolean isCanReview() { return canReview; }

    // Por que no fue al evento (null si fue, o si el evento todavia no paso).
    public NotAttendedReason getNotAttendedReason() { return notAttendedReason; }

    // Puede darse de baja: el evento sigue vigente (misma regla que RegistrationService.cancel()).
    public boolean isCanCancel() { return canCancel; }
}
