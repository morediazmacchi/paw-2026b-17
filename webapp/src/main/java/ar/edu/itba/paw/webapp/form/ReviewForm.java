// Datos del formulario de "dejar reseña" (perfil, eventos finalizados),
// validados con JSR-303.
package ar.edu.itba.paw.webapp.form;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class ReviewForm {

    @NotNull(message = "Elegí una calificación")
    @Min(value = 0, message = "La calificación tiene que ser entre 0 y 5")
    @Max(value = 5, message = "La calificación tiene que ser entre 0 y 5")
    private Integer rating;

    @Size(max = 1000, message = "El comentario no puede superar los 1000 caracteres")
    private String comment;

    public ReviewForm() {
    }

    public Integer getRating() { return rating; }
    public void setRating(final Integer rating) { this.rating = rating; }

    public String getComment() { return comment; }
    public void setComment(final String comment) { this.comment = comment; }
}
