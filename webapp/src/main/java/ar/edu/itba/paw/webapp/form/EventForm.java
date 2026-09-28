// Datos del formulario de publicar evento, validados con JSR-303.
package ar.edu.itba.paw.webapp.form;

import javax.validation.Valid;
import javax.validation.constraints.Digits;
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

    // Info de la direccion que Nominatim no geocodifica (piso, depto,
    // referencias): texto libre y opcional, nunca se manda a buscar en el mapa.
    @Size(max = 300, message = "El detalle de la direccion no puede superar los 300 caracteres")
    private String detalleDireccion;

    @NotBlank(message = "Elegí un barrio")
    @Size(max = 80, message = "El barrio no puede superar los 80 caracteres")
    private String barrio;

    // Pin en el mapa (ver event-location-map.js): dos inputs ocultos que el JS
    // completa, segun el modo que haya elegido la persona (botones "con
    // geolocalizacion" / "en texto plano" del paso 3, ninguno de los dos es
    // un campo del form: solo deciden que controles del mismo paso se ven).
    // Sin JSR-303 a proposito -- si vienen vacios o con basura (todavia no se
    // puso ningun pin, o alguien manipulo el request) el controller los
    // ignora en vez de bloquear la publicacion por un campo que ni siquiera
    // es obligatorio.
    private String latitud;
    private String longitud;

    // "true"/"false": si el pin de arriba (cuando hay uno) lo puso la persona
    // a mano (click en el mapa, modo "texto plano") o event-location-map.js
    // al elegir una sugerencia de Nominatim (modo "con geolocalizacion").
    // Igual que latitud/longitud, sin JSR-303: el controller (ver
    // EventController.applyPin()) por defecto asume "a mano" (el mas cauto)
    // salvo que venga explicitamente "false".
    private String pinManual;

    // @Digits acota a lo que entra en la columna price, NUMERIC(10,2) (hasta
    // 8 digitos enteros): sin esto, un precio mas grande pasa el @Pattern (que
    // solo valida la forma) y explota como DataIntegrityViolationException
    // recien al insertar, que EventServiceImpl.create() interpreta como
    // "la categoria no existe" -- un error total mente enganoso para quien
    // solo escribio un precio de mas.
    @NotBlank(message = "El precio es obligatorio (0 si es evento gratuito)")
    @Pattern(regexp = "^[0-9]+(\\.[0-9]{1,2})?$", message = "El precio tiene que ser un numero valido, ej: 0 o 1500.50")
    @Digits(integer = 8, fraction = 2, message = "El precio no puede tener mas de 8 digitos enteros")
    private String precio;

    // Mismo motivo que el @Digits de precio: sin cota, un cupo mas grande que
    // Integer.MAX_VALUE hace que Integer.valueOf() explote con
    // NumberFormatException sin capturar en el controller.
    @NotBlank(message = "El cupo maximo es obligatorio (0 si no hay limite)")
    @Pattern(regexp = "^[0-9]+$", message = "El cupo maximo tiene que ser un numero entero")
    @Digits(integer = 9, fraction = 0, message = "El cupo maximo no puede tener mas de 9 digitos")
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

    // Mismo criterio que privado/requiereAprobacion: checkbox comun, ausente = false.
    private boolean estacionamiento;

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

    public String getDetalleDireccion() { return detalleDireccion; }
    public void setDetalleDireccion(final String detalleDireccion) { this.detalleDireccion = detalleDireccion; }

    public String getBarrio() { return barrio; }
    public void setBarrio(final String barrio) { this.barrio = barrio; }

    public String getLatitud() { return latitud; }
    public void setLatitud(final String latitud) { this.latitud = latitud; }

    public String getLongitud() { return longitud; }
    public void setLongitud(final String longitud) { this.longitud = longitud; }

    public String getPinManual() { return pinManual; }
    public void setPinManual(final String pinManual) { this.pinManual = pinManual; }

    public String getPrecio() { return precio; }
    public void setPrecio(final String precio) { this.precio = precio; }

    public String getCupoMaximo() { return cupoMaximo; }
    public void setCupoMaximo(final String cupoMaximo) { this.cupoMaximo = cupoMaximo; }

    public boolean isRequiereAprobacion() { return requiereAprobacion; }
    public void setRequiereAprobacion(final boolean requiereAprobacion) { this.requiereAprobacion = requiereAprobacion; }

    public boolean isPrivado() { return privado; }
    public void setPrivado(final boolean privado) { this.privado = privado; }

    public boolean isEstacionamiento() { return estacionamiento; }
    public void setEstacionamiento(final boolean estacionamiento) { this.estacionamiento = estacionamiento; }
}
