package ar.edu.itba.paw.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class Event {

    private long id;
    private String title;
    private String description;
    private Date eventDate;
    private String address;
    private Barrio neighborhood;
    // Un evento puede tener varias fotos (relacion 1:N, ver event_image en
    // schema.sql), en orden de subida. No hay getImageId()/setImageId() como
    // columna propia: getImageId() (mas abajo) deriva la portada de esta
    // lista para no tener que tocar los varios JSP que ya la usan (cards,
    // "Mis eventos", favoritos, inscripciones).
    private List<Long> imageIds = new ArrayList<>();
    // BigDecimal y no double: el precio es plata. double es binario y no representa
    // exactamente valores como 0.10; BigDecimal si, igual que la columna NUMERIC(10,2).
    private BigDecimal price = BigDecimal.ZERO;
    private Integer maxCapacity;
    private String creatorEmail;
    // FK de verdad contra users.id (ver schema.sql): de aca en mas es la
    // fuente de la verdad para "quien es el dueno de este evento" (las
    // comparaciones de ownership usan esto, no mas creatorEmail). Null solo
    // puede pasar en una fila vieja que todavia no paso por el backfill de
    // postgres-migrations.sql; toda fila nueva la trae siempre resuelta
    // desde el Principal autenticado (ver EventController). No va en el
    // constructor por el mismo motivo que requiresApproval: no romper los
    // "new Event(...)" existentes (muchos en tests).
    private Long creatorId;
    // Un evento puede tener varias categorias (relacion N:M, ver event_category
    // en schema.sql). categoryName y extraInfo de cada una solo se llenan al
    // leer (join con category / event_category), no hace falta setearlos para
    // crear o editar un evento.
    private List<EventCategoryInfo> categories = new ArrayList<>();
    private Date createdAt;
    // Si esta en true, una inscripcion nueva nace 'pending' en vez de
    // 'confirmed': el creador la tiene que aceptar o rechazar el mismo. No va
    // en el constructor de abajo (igual que categoryName/createdAt) para no
    // romper los call sites existentes; se setea aparte antes de guardar.
    private boolean requiresApproval;
    // PUBLIC por default: un "new Event()" sin tocar este campo (hay muchos
    // en los tests) tiene que comportarse como siempre se comporto, visible
    // para cualquiera.
    private EventVisibility visibility = EventVisibility.PUBLIC;
    // null = activo. Con fecha = cancelado (ver EventDao.cancel()). Mismo
    // motivo que requiresApproval/visibility para no ir en el constructor:
    // no romper los "new Event(...)" existentes.
    private Date cancelledAt;
    // Pin en el mapa (ver publish.jsp/editar.jsp): el creador elige, de forma
    // explicita, entre buscar la direccion contra Nominatim (mapa de solo
    // lectura, pin geocodificado) o cargarla a mano (mapa editable con click,
    // ver locationManual mas abajo). Null en los dos significa que todavia no
    // se puso ningun pin (eventos viejos, o el creador no llego a confirmar).
    // Mismo motivo que cancelledAt para no ir en el constructor.
    private Double latitude;
    private Double longitude;
    // true = el pin de arriba lo puso el creador a mano (click en el mapa);
    // false = lo puso event-location-map.js al elegir una sugerencia de
    // Nominatim. Solo importa una vez que hay pin (ver isLocationPinned()):
    // el detalle lo usa para advertir "puede no ser exacto" solo en el caso
    // manual (frecuente en parques/plazas grandes, donde Nominatim no siempre
    // puede geocodificar). Default true (el mas cauto: "no asumas precision")
    // a proposito, igual que la columna -- asi ningun pin viejo (de antes de
    // esta distincion, todos puestos a mano) queda mal etiquetado como
    // geocodificado.
    private boolean locationManual = true;
    // Si el evento cuenta con estacionamiento para los asistentes. Mismo
    // motivo que requiresApproval para no ir en el constructor: no romper los
    // "new Event(...)" existentes. false por default, igual que la columna.
    private boolean parking;
    // Info de la direccion que Nominatim no puede geocodificar (piso, depto,
    // referencias): texto libre y opcional, nunca se manda a geocodificar,
    // solo se muestra junto a "address" en el detalle. Null = nadie cargo nada.
    private String addressDetails;
    // Baja por moderacion (ver ModerationService.removeEvent()): usa el MISMO
    // mecanismo que cancel() (cancelledAt, ver mas arriba), asi que todo lo
    // que ya trata a un evento cancelado como inactivo (busquedas, "editar",
    // inscribirse, preguntas) automaticamente tambien excluye a un evento
    // removido, sin duplicar esa logica. Este flag solo distingue el motivo
    // para poder avisarle al creador y mostrar el mensaje correcto ("un admin
    // lo dio de baja" en vez de "vos lo cancelaste"). false por default: un
    // "new Event(...)" o una cancelacion comun no lo tocan.
    private boolean removedByAdmin;
    // Motivo que cargo el admin al dar de baja (ver AdminController). Null si
    // el evento nunca fue removido por moderacion.
    private String removedReason;

    public Event() {
    }

    // El ultimo parametro reemplazo al viejo "long categoryId" unico: ahora un
    // evento puede tener varias categorias, sin informacion extra por
    // categoria (para eso esta setCategories(), usado por el wizard de
    // publicar/editar, que si carga extraInfo por cada una).
    public Event(final String title, final String description, final Date eventDate, final String address,
                 final Barrio neighborhood, final List<Long> imageIds, final BigDecimal price, final Integer maxCapacity,
                 final String creatorEmail, final List<Long> categoryIds) {
        this.title = title;
        this.description = description;
        this.eventDate = eventDate;
        this.address = address;
        this.neighborhood = neighborhood;
        this.imageIds = imageIds == null ? new ArrayList<>() : imageIds;
        this.price = price;
        this.maxCapacity = maxCapacity;
        this.creatorEmail = creatorEmail;
        this.categories = categoryIds == null ? new ArrayList<>()
                : categoryIds.stream().map(EventCategoryInfo::new).collect(Collectors.toList());
    }

    public long getId() { return id; }
    public void setId(final long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(final String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(final String description) { this.description = description; }
    public Date getEventDate() { return eventDate; }
    public void setEventDate(final Date eventDate) { this.eventDate = eventDate; }
    public String getAddress() { return address; }
    public void setAddress(final String address) { this.address = address; }
    public Barrio getNeighborhood() { return neighborhood; }
    public void setNeighborhood(final Barrio neighborhood) { this.neighborhood = neighborhood; }
    // La portada: la primera foto de la galeria, o null si el evento no tiene
    // ninguna. Se usa en todos lados donde hace falta una sola miniatura
    // (tarjetas, "Mis eventos", favoritos, inscripciones); getImageIds()
    // (mas abajo) es la galeria completa, para el detalle.
    public Long getImageId() { return imageIds.isEmpty() ? null : imageIds.get(0); }
    public List<Long> getImageIds() { return imageIds; }
    public void setImageIds(final List<Long> imageIds) {
        this.imageIds = imageIds == null ? new ArrayList<>() : imageIds;
    }
    public BigDecimal getPrice() { return price; }
    public void setPrice(final BigDecimal price) { this.price = price; }
    public Integer getMaxCapacity() { return maxCapacity; }
    public void setMaxCapacity(final Integer maxCapacity) { this.maxCapacity = maxCapacity; }
    public String getCreatorEmail() { return creatorEmail; }
    public void setCreatorEmail(final String creatorEmail) { this.creatorEmail = creatorEmail; }
    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(final Long creatorId) { this.creatorId = creatorId; }
    public List<EventCategoryInfo> getCategories() { return categories; }
    public void setCategories(final List<EventCategoryInfo> categories) {
        this.categories = categories == null ? new ArrayList<>() : categories;
    }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(final Date createdAt) { this.createdAt = createdAt; }
    public boolean isRequiresApproval() { return requiresApproval; }
    public void setRequiresApproval(final boolean requiresApproval) { this.requiresApproval = requiresApproval; }
    public EventVisibility getVisibility() { return visibility; }
    public void setVisibility(final EventVisibility visibility) { this.visibility = visibility; }
    public Date getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(final Date cancelledAt) { this.cancelledAt = cancelledAt; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(final Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(final Double longitude) { this.longitude = longitude; }
    public boolean isLocationManual() { return locationManual; }
    public void setLocationManual(final boolean locationManual) { this.locationManual = locationManual; }
    public boolean isParking() { return parking; }
    public void setParking(final boolean parking) { this.parking = parking; }
    public String getAddressDetails() { return addressDetails; }
    public void setAddressDetails(final String addressDetails) { this.addressDetails = addressDetails; }
    public boolean isRemovedByAdmin() { return removedByAdmin; }
    public void setRemovedByAdmin(final boolean removedByAdmin) { this.removedByAdmin = removedByAdmin; }
    public String getRemovedReason() { return removedReason; }
    public void setRemovedReason(final String removedReason) { this.removedReason = removedReason; }

    // Los dos vienen juntos o ninguno (ver EventController): si falta uno el
    // otro no sirve para nada, mejor no mostrar un pin a medias. "is..." (no
    // "has...") a proposito: es el prefijo que la EL de los JSP reconoce como
    // getter de un boolean (${event.locationPinned}), mismo criterio que
    // isPrivate()/isFree()/isCancelled() aca arriba.
    public boolean isLocationPinned() { return latitude != null && longitude != null; }

    public boolean isFree() { return price == null || price.signum() == 0; }
    public boolean isPrivate() { return visibility == EventVisibility.PRIVATE; }
    public boolean isCancelled() { return cancelledAt != null; }
}
