// Datos del formulario de "dejar reseña" (perfil, eventos finalizados),
// validados con JSR-303.
package ar.edu.itba.paw.webapp.form;

import ar.edu.itba.paw.services.ReviewService;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class ReviewForm {

    @NotNull
    @Min(value = ReviewService.MIN_RATING)
    @Max(value = ReviewService.MAX_RATING)
    private Integer rating;

    @Size(max = ReviewService.MAX_COMMENT_LENGTH)
    private String comment;

    public ReviewForm() {
    }

    public Integer getRating() { return rating; }
    public void setRating(final Integer rating) { this.rating = rating; }

    public String getComment() { return comment; }
    public void setComment(final String comment) { this.comment = comment; }
}
