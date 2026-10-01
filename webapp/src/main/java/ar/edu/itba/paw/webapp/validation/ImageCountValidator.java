package ar.edu.itba.paw.webapp.validation;

import org.springframework.web.multipart.MultipartFile;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.List;

public class ImageCountValidator implements ConstraintValidator<ValidImageCount, List<MultipartFile>> {

    private int max;

    @Override
    public void initialize(final ValidImageCount annotation) {
        this.max = annotation.max();
    }

    @Override
    public boolean isValid(final List<MultipartFile> value, final ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        final long nonEmptyCount = value.stream().filter(f -> f != null && !f.isEmpty()).count();
        return nonEmptyCount <= max;
    }
}
