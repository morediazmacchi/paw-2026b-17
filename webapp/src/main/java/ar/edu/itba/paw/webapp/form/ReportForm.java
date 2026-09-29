package ar.edu.itba.paw.webapp.form;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

// Motivo de un reporte de evento (EventController.reportar()).
public class ReportForm {

    @NotBlank
    @Size(max = 500)
    private String motivo;

    public String getMotivo() { return motivo; }
    public void setMotivo(final String motivo) { this.motivo = motivo; }
}
