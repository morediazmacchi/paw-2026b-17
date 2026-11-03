package ar.edu.itba.paw.webapp.form;

import ar.edu.itba.paw.services.ModerationService;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

// Motivo de un reporte de evento (EventController.reportar()).
public class ReportForm {

    @NotBlank
    @Size(max = ModerationService.MAX_REPORT_REASON_LENGTH)
    private String motivo;

    public String getMotivo() { return motivo; }
    public void setMotivo(final String motivo) { this.motivo = motivo; }
}
