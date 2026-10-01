// Datos del formulario de publicar evento, validados con JSR-303.
package ar.edu.itba.paw.webapp.form;

import ar.edu.itba.paw.dto.EventDTO;
import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.Category;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventCategoryInfo;
import ar.edu.itba.paw.model.EventVisibility;
import ar.edu.itba.paw.webapp.controller.EventController;
import ar.edu.itba.paw.webapp.validation.ValidBarrio;
import ar.edu.itba.paw.webapp.validation.ValidCategorias;
import ar.edu.itba.paw.webapp.validation.ValidEventDate;
import ar.edu.itba.paw.webapp.validation.ValidImageCount;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class EventForm {

    @NotBlank(message = "El titulo es obligatorio")
    @Size(max = 150, message = "El titulo no puede superar los 150 caracteres")
    private String titulo;

    @Size(max = 1000, message = "La descripcion no puede superar los 1000 caracteres")
    private String descripcion;

    // Una entrada por cada categoria existente (ver EventController), en vez
    // de un solo id: ahora se puede elegir mas de una. @ValidCategorias chequea
    // que haya al menos una tildada y que las tildadas existan de verdad.
    @Valid
    @ValidCategorias
    private List<CategoriaSeleccionForm> categorias = new ArrayList<>();

    @NotBlank(message = "La fecha y horario son obligatorios")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}$", message = "La fecha y horario no tienen un formato valido")
    @ValidEventDate
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
    @ValidBarrio
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

    // Sin @RequestParam aparte en el controller: Spring bindea los MultipartFile
    // del <input type="file" name="images" multiple> directo a esta propiedad,
    // por nombre, igual que bindea cualquier otro campo del form. @ValidImageCount
    // reemplaza el chequeo manual de MAX_IMAGES que antes hacia el controller.
    @ValidImageCount(max = EventController.MAX_IMAGES)
    private List<MultipartFile> images = new ArrayList<>();

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

    public List<MultipartFile> getImages() { return images; }
    public void setImages(final List<MultipartFile> images) { this.images = images; }

    // Arma el DTO que recibe EventService.create(). Solo se tiene que llamar
    // despues de que @Valid paso sin errores: todo parseo de aca asume que
    // fechaEvento/barrio/precio/cupoMaximo/categorias ya son validos (lo
    // garantizan @ValidEventDate, @ValidBarrio, los @Pattern/@Digits y
    // @ValidCategorias), asi que no hace falta try/catch para nada de esto.
    public EventDTO toDTO() {
        final EventDTO dto = new EventDTO();
        dto.setTitle(titulo);
        dto.setDescription(descripcion);
        dto.setCategories(getSelectedCategories());
        dto.setEventDate(parseEventDate(fechaEvento));
        dto.setAddress(direccion);
        dto.setAddressDetails(detalleDireccion);
        dto.setNeighborhood(Barrio.valueOf(barrio));
        dto.setPrice(new BigDecimal(precio));
        // "0" es el valor que usa el atajo "Sin limite" (ver quick-options.js).
        dto.setMaxCapacity("0".equals(cupoMaximo) ? null : Integer.valueOf(cupoMaximo));
        dto.setRequiresApproval(requiereAprobacion);
        dto.setVisibility(privado ? EventVisibility.PRIVATE : EventVisibility.PUBLIC);
        dto.setParking(estacionamiento);
        applyPin(dto);
        return dto;
    }

    // Las categorias tildadas, mapeadas a EventCategoryInfo (con su extraInfo
    // recortado). Que haya al menos una ya lo garantiza @ValidCategorias antes
    // de que se llegue a llamar esto. Publica porque EventController.editar()
    // tambien la necesita para armar los cambios a guardar.
    public List<EventCategoryInfo> getSelectedCategories() {
        return categorias.stream()
                .filter(CategoriaSeleccionForm::isSeleccionada)
                .map(c -> new EventCategoryInfo(c.getCategoryId(), blankToNull(c.getExtraInfo())))
                .collect(Collectors.toList());
    }

    private static Date parseEventDate(final String value) {
        final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");
        format.setLenient(false);
        try {
            return format.parse(value);
        } catch (final ParseException e) {
            // No deberia pasar nunca: @ValidEventDate ya garantizo que esto parsea.
            throw new IllegalStateException("fechaEvento invalida a pesar de @ValidEventDate: " + value, e);
        }
    }

    private static String blankToNull(final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // Pin del mapa (opcional): solo se guarda si llegan las DOS coordenadas, son
    // numeros y estan dentro de rango (latitud -90..90, longitud -180..180). Fuera
    // de eso se ignora en silencio: es un dato opcional, y un valor absurdo (o que
    // no entra en NUMERIC(9,6)) no tiene que tumbar la publicacion ni guardarse a medias.
    private void applyPin(final EventDTO dto) {
        final Double lat = parseCoordinate(latitud);
        final Double lon = parseCoordinate(longitud);
        final boolean valid = lat != null && lon != null
                && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
        dto.setLatitude(valid ? lat : null);
        dto.setLongitude(valid ? lon : null);
        // Default "a mano" (el mas cauto): solo queda en false si event-location-map.js
        // lo marco explicitamente al elegir una sugerencia de Nominatim con exito.
        dto.setLocationManual(!"false".equals(pinManual));
    }

    // El pin del mapa es un campo oculto que solo el JS completa: si vino vacio
    // (nadie toco el mapa) o con algo que no es un numero (request manipulado a
    // mano), se ignora en silencio en vez de rechazar la publicacion entera por
    // un dato que ni siquiera es obligatorio.
    private static Double parseCoordinate(final String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(value.trim());
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    // Un EventForm vacio para la primera carga de "Publicar": todas las
    // categorias existen sin tildar, para que el binding indexado de Spring
    // (categorias[0].seleccionada, etc.) este sincronizado con el JSP desde el
    // primer GET, antes de cualquier submit.
    public static EventForm empty(final List<Category> categories) {
        final EventForm form = new EventForm();
        form.setCategorias(categoriaFormsFor(Collections.emptyList(), categories));
        return form;
    }

    // Un CategoriaSeleccionForm por cada categoria existente: "existing" trae
    // las que el evento ya tiene (si las hay) para tildarlas y precargar su
    // extraInfo; el resto queda sin tildar. Publico porque tambien lo usa
    // EventController al precargar "Editar" con los datos de un evento existente.
    public static List<CategoriaSeleccionForm> categoriaFormsFor(
            final List<EventCategoryInfo> existing, final List<Category> allCategories) {
        final Map<Long, EventCategoryInfo> byId = existing.stream()
                .collect(Collectors.toMap(EventCategoryInfo::getCategoryId, c -> c));
        return allCategories.stream().map(cat -> {
            final CategoriaSeleccionForm categoriaForm = new CategoriaSeleccionForm();
            categoriaForm.setCategoryId(cat.getId());
            final EventCategoryInfo info = byId.get(cat.getId());
            categoriaForm.setSeleccionada(info != null);
            categoriaForm.setExtraInfo(info == null ? null : info.getExtraInfo());
            return categoriaForm;
        }).collect(Collectors.toList());
    }

    // Precarga "Editar" con los datos actuales del evento.
    public static EventForm from(final Event event, final List<Category> categories) {
        final EventForm form = new EventForm();
        form.setTitulo(event.getTitle());
        form.setDescripcion(event.getDescription());
        form.setCategorias(categoriaFormsFor(event.getCategories(), categories));
        form.setFechaEvento(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm").format(event.getEventDate()));
        form.setDireccion(event.getAddress());
        form.setDetalleDireccion(event.getAddressDetails());
        form.setBarrio(event.getNeighborhood() == null ? null : event.getNeighborhood().name());
        form.setPrecio(formatDecimal(event.getPrice()));
        // "0" es el valor que usa el atajo "Sin limite" (quick-options.js lo
        // detecta por igualdad exacta de string) -- mismo criterio que publish().
        form.setCupoMaximo(event.getMaxCapacity() == null ? "0" : String.valueOf(event.getMaxCapacity()));
        form.setRequiereAprobacion(event.isRequiresApproval());
        form.setPrivado(event.isPrivate());
        form.setEstacionamiento(event.isParking());
        if (event.isLocationPinned()) {
            form.setLatitud(String.valueOf(event.getLatitude()));
            form.setLongitud(String.valueOf(event.getLongitude()));
            // Arranca marcando el modo (geolocalizacion/texto plano, ver
            // publish.jsp/editar.jsp) que corresponde a como quedo el pin la
            // ultima vez, para no perder esa eleccion al reabrir "Editar".
            form.setPinManual(String.valueOf(event.isLocationManual()));
        }
        return form;
    }

    // 2500.00 -> "2500" (sin decimales de sobra), 1500.50 -> "1500.5". Los dos
    // formatos matchean el @Pattern de precio. 0 es un caso aparte:
    // BigDecimal.ZERO.stripTrailingZeros() no es confiable entre versiones de
    // Java (bug historico que a veces devuelve "0E-...").
    private static String formatDecimal(final BigDecimal value) {
        if (value == null || value.signum() == 0) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }
}
