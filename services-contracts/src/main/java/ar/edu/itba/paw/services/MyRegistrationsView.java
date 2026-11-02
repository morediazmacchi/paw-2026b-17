// "Mis inscripciones": las proximas y las pasadas como dos resultados paginados independientes, cada uno con
// su propio page/totalPages/totalElements. Lo devuelve RegistrationService.findMyRegistrations().
package ar.edu.itba.paw.services;

public class MyRegistrationsView {

    private final PageResult<RegistrationEntry> upcoming;
    private final PageResult<RegistrationEntry> past;

    public MyRegistrationsView(final PageResult<RegistrationEntry> upcoming, final PageResult<RegistrationEntry> past) {
        this.upcoming = upcoming;
        this.past = past;
    }

    // Inscripciones a eventos que todavia no pasaron, el mas proximo primero.
    public PageResult<RegistrationEntry> getUpcoming() { return upcoming; }

    // Inscripciones a eventos pasados, el mas reciente primero.
    public PageResult<RegistrationEntry> getPast() { return past; }
}
