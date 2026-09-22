// Datos del formulario de publicar evento, validados con JSR-303.
package ar.edu.itba.paw.webapp.form;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

public class EventForm {

    @NotBlank(message = "El titulo es obligatorio")
    @Size(max = 150, message = "El titulo no puede superar los 150 caracteres")
    private String titulo;

    @Size(max = 1000, message = "La descripcion no puede superar los 1000 caracteres")
    private String descripcion;

    // Una entrada por cada categoria existente (ver EventController), en vez
    // de un solo id: ahora se puede elegir mas de una. Que haya elegido al
    // menos una no se puede expresar con JSR-303 sobre esta lista (todas sus
    // entradas existen siempre, tildadas o no, para que el binding indexado
    // de Spring funcione); ese chequeo lo hace el controller a mano, como ya
    // hacia con el barrio.
    @Valid
    private List<CategoriaSeleccionForm> categorias = new ArrayList<>();

    @NotBlank(message = "La fecha y horario son obligatorios")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}$", message = "La fecha y horario no tienen un formato valido")
    private String fechaEvento;

    @NotBlank(message = "La direccion es obligatoria")
    @Size(max = 200, message = "La direccion no puede superar los 200 caracteres")
    private String direccion;

    @NotBlank(message = "Elegí un barrio")
    @Size(max = 80, message = "El barrio no puede superar los 80 caracteres")
    private String barrio;

    @NotBlank(message = "El precio es obligatorio (0 si es evento gratuito)")
    @Pattern(regexp = "^[0-9]+(\\.[0-9]{1,2})?$", message = "El precio tiene que ser un numero valido, ej: 0 o 1500.50")
    private String precio;

    @NotBlank(message = "El cupo maximo es obligatorio (0 si no hay limite)")
    @Pattern(regexp = "^[0-9]+$", message = "El cupo maximo tiene que ser un numero entero")
    private String cupoMaximo;

    // Checkbox comun (sin JSR-303: ausente en el request = false, no hay nada
    // que validar). Si esta en true, las inscripciones nuevas nacen 'pending'
    // en vez de 'confirmed': el creador las tiene que aceptar o rechazar el
    // mismo (ver RegistrationServiceImpl.register()).
    private boolean requiereAprobacion;

    // Mismo criterio que requiereAprobacion: checkbox comun, ausente = false.
    // Si esta en true, el evento no aparece en el listado ni en las busquedas
    // (ver EventVisibility).
    private boolean privado;

    public EventForm() {
    }

    public String getTitulo() { return titulo; }
    public void setTitulo(final String titulo) { this.titulo = titulo; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(final String descripcion) { this.descripcion = descripcion; }

    public List<CategoriaSeleccionForm> getCategorias() { return categorias; }
    public void setCategorias(final List<CategoriaSeleccionForm> categorias) { this.categorias = categorias; }

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

    public boolean isRequiereAprobacion() { return requiereAprobacion; }
    public void setRequiereAprobacion(final boolean requiereAprobacion) { this.requiereAprobacion = requiereAprobacion; }

    public boolean isPrivado() { return privado; }
    public void setPrivado(final boolean privado) { this.privado = privado; }
}
