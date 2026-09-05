// Datos del formulario de publicar evento, validados con JSR-303.
package ar.edu.itba.paw.webapp.form;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class EventForm {

    @NotBlank(message = "El titulo es obligatorio")
    @Size(max = 150, message = "El titulo no puede superar los 150 caracteres")
    private String titulo;

    @Size(max = 1000, message = "La descripcion no puede superar los 1000 caracteres")
    private String descripcion;

    @NotNull(message = "Elegi una categoria")
    private Long categoriaId;

    @NotBlank(message = "La fecha y horario son obligatorios")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}$", message = "La fecha y horario no tienen un formato valido")
    private String fechaEvento;

    @NotBlank(message = "La direccion es obligatoria")
    @Size(max = 200, message = "La direccion no puede superar los 200 caracteres")
    private String direccion;

    @Size(max = 80, message = "El barrio no puede superar los 80 caracteres")
    private String barrio;

    @NotBlank(message = "El precio es obligatorio (0 si es evento gratuito)")
    @Pattern(regexp = "^[0-9]+(\\.[0-9]{1,2})?$", message = "El precio tiene que ser un numero valido, ej: 0 o 1500.50")
    private String precio;

    @NotBlank(message = "El cupo maximo es obligatorio (0 si no hay limite)")
    @Pattern(regexp = "^[0-9]+$", message = "El cupo maximo tiene que ser un numero entero")
    private String cupoMaximo;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email no tiene un formato valido")
    private String creadorEmail;

    public EventForm() {
    }

    public String getTitulo() { return titulo; }
    public void setTitulo(final String titulo) { this.titulo = titulo; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(final String descripcion) { this.descripcion = descripcion; }

    public Long getCategoriaId() { return categoriaId; }
    public void setCategoriaId(final Long categoriaId) { this.categoriaId = categoriaId; }

    public String getFechaEvento() { return fechaEvento; }
    public void setFechaEvento(final String fechaEvento) { this.fechaEvento = fechaEvento; }

    public String getDireccion() { return direccion; }
    public void setDireccion(final String direccion) { this.direccion = direccion; }

    public String getBarrio() { return barrio; }
    public void setBarrio(final String barrio) { this.barrio = barrio; }

    public String getPrecio() { return precio; }
    public void setPrecio(final String precio) { this.precio = precio; }

    public String getCupoMaximo() { return cupoMaximo; }
    public void setCupoMaximo(final String cupoMaximo) { this.cupoMaximo = cupoMaximo; }

    public String getCreadorEmail() { return creadorEmail; }
    public void setCreadorEmail(final String creadorEmail) { this.creadorEmail = creadorEmail; }
}
