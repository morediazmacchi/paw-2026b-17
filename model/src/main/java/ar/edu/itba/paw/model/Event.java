package ar.edu.itba.paw.model;

import org.hibernate.annotations.Formula;

import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "event")
public class Event {

    public static final int MAX_IMAGES = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "title", length = 150, nullable = false)
    private String title;
    @Column(name = "description")
    private String description;
    @Column(name = "event_date", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date eventDate;
    @Column(name = "address", length = 200, nullable = false)
    private String address;
    @Column(name = "neighborhood", length = 80)
    @Enumerated(EnumType.STRING)
    private Barrio neighborhood;
    // Un evento puede tener varias fotos (relacion 1:N, ver event_image en schema.sql), en orden
    // de subida. Se guardan solo los NUMEROS de las imagenes (no los objetos Image con sus bytes):
    // alcanza para armar la URL de cada foto, y no se cargan los archivos enteros de la base cada
    // vez que se lee un evento. @ElementCollection = una coleccion de valores simples que vive en
    // otra tabla (event_image); el orden es por image_id, que crece con cada foto subida.
    // getImageId() (mas abajo) deriva la portada de esta lista para no tener que tocar los varios
    // JSP que ya la usan (cards, "Mis eventos", favoritos, inscripciones).
    @ElementCollection
    @CollectionTable(name = "event_image", joinColumns = @JoinColumn(name = "event_id"))
    @Column(name = "image_id", nullable = false)
    @OrderBy
    private List<Long> imageIds = new ArrayList<>();
    // La portada (la primera foto) la calcula la base en el mismo SELECT del evento:
    // @Formula = "este campo no es una columna, es esta expresion SQL". Asi las tarjetas y
    // listados muestran la portada SIN cargar la lista imageIds entera (que seria traer todas
    // las fotos de cada evento solo para usar una). Es de solo lectura: se escribe a traves de
    // imageIds. El "id" de la subconsulta es el id del propio evento.
    @Formula("(SELECT MIN(ei.image_id) FROM event_image ei WHERE ei.event_id = id)")
    private Long coverImageId;
    // BigDecimal y no double: el precio es plata. double es binario y no representa
    // exactamente valores como 0.10; BigDecimal si, igual que la columna NUMERIC(10,2).
    @Column(name = "price", precision = 10, scale = 2, nullable = false)
    private BigDecimal price = BigDecimal.ZERO;
    @Column(name = "max_capacity")
    private Integer maxCapacity;
    @Column(name = "creator_email", length = 150, nullable = false)
    private String creatorEmail;
    // Dueno del evento (FK contra users.id, ver schema.sql). Es la fuente de la verdad para "quien
    // es el dueno de este evento" (las comparaciones de ownership usan getCreatorId(), no mas
    // creatorEmail). Null solo puede pasar en una fila vieja que todavia no paso por el backfill
    // de postgres-migrations.sql; toda fila nueva lo trae resuelto desde el usuario autenticado
    // (ver EventController).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private User creator;
    // Un evento puede tener varias categorias (relacion N:M, ver event_category en schema.sql).
    // Cada fila de event_category tiene ademas un dato propio (extraInfo), asi que la relacion se
    // modela con una entidad intermedia (EventCategory). "mappedBy": la relacion la maneja el
    // atributo "event" de EventCategory; cascade + orphanRemoval: guardar/borrar el evento
    // guarda/borra sus categorias, y sacar una de la lista borra su fila. Se agregan y sacan con
    // addCategory()/removeCategory() para mantener los dos lados de la relacion de acuerdo.
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EventCategory> categories = new ArrayList<>();
    // La completa la base (DEFAULT now()): no se inserta ni se actualiza desde Java.
    @Column(name = "created_at", insertable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;
    // Si esta en true, una inscripcion nueva nace 'pending' en vez de
    // 'confirmed': el creador la tiene que aceptar o rechazar el mismo. No va
    // en el constructor de abajo (igual que categoryName/createdAt) para no
    // romper los call sites existentes; se setea aparte antes de guardar.
    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval;
    // PUBLIC por default: un "new Event()" sin tocar este campo (hay muchos
    // en los tests) tiene que comportarse como siempre se comporto, visible
    // para cualquiera.
    @Column(name = "visibility", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private EventVisibility visibility = EventVisibility.PUBLIC;
    // null = activo. Con fecha = cancelado (ver EventDao.cancel()). Mismo
    // motivo que requiresApproval/visibility para no ir en el constructor:
    // no romper los "new Event(...)" existentes.
    @Column(name = "cancelled_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date cancelledAt;
    // Pin en el mapa (ver publish.jsp/editar.jsp): el creador elige, de forma
    // explicita, entre buscar la direccion contra Nominatim (mapa de solo
    // lectura, pin geocodificado) o cargarla a mano (mapa editable con click,
    // ver locationManual mas abajo). Null en los dos significa que todavia no
    // se puso ningun pin (eventos viejos, o el creador no llego a confirmar).
    // Mismo motivo que cancelledAt para no ir en el constructor.
    @Column(name = "latitude", precision = 9, scale = 6)
    private Double latitude;
    @Column(name = "longitude", precision = 9, scale = 6)
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
    @Column(name = "location_manual", nullable = false)
    private boolean locationManual = true;
    // Si el evento cuenta con estacionamiento para los asistentes. Mismo
    // motivo que requiresApproval para no ir en el constructor: no romper los
    // "new Event(...)" existentes. false por default, igual que la columna.
    @Column(name = "has_parking", nullable = false)
    private boolean parking;
    // Info de la direccion que Nominatim no puede geocodificar (piso, depto,
    // referencias): texto libre y opcional, nunca se manda a geocodificar,
    // solo se muestra junto a "address" en el detalle. Null = nadie cargo nada.
    @Column(name = "address_details", length = 300)
    private String addressDetails;
    // Baja por moderacion (ver ModerationService.removeEvent()): usa el MISMO
    // mecanismo que cancel() (cancelledAt, ver mas arriba), asi que todo lo
    // que ya trata a un evento cancelado como inactivo (busquedas, "editar",
    // inscribirse, preguntas) automaticamente tambien excluye a un evento
    // removido, sin duplicar esa logica. Este flag solo distingue el motivo
    // para poder avisarle al creador y mostrar el mensaje correcto ("un admin
    // lo dio de baja" en vez de "vos lo cancelaste"). false por default: un
    // "new Event(...)" o una cancelacion comun no lo tocan.
    @Column(name = "removed_by_admin", nullable = false)
    private boolean removedByAdmin;
    // Motivo que cargo el admin al dar de baja (ver AdminController). Null si
    // el evento nunca fue removido por moderacion.
    @Column(name = "removed_reason", length = 500)
    private String removedReason;
    // Un admin lo eligio para el banner de /eventos (ver EventController.eventos()
    // y AdminController.destacar()). featuredAt ordena el banner (mas reciente
    // primero) y se limpia al desmarcar: si se vuelve a destacar despues, entra
    // "de nuevo" al tope, no donde habia quedado la primera vez.
    @Column(name = "featured", nullable = false)
    private boolean featured;
    @Column(name = "featured_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date featuredAt;
    // Recordatorio "falta menos de 24 hs" (ver EventReminderScheduler): null = todavia
    // no se mando. Lo maneja EventJpaDao (markReminderSent, y update() cuando cambia
    // la fecha); no va en el constructor ni lo setea el resto del codigo.
    @Column(name = "reminder_sent_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date reminderSentAt;

    public Event() {
    }

    public Event(final String title, final String description, final Date eventDate, final String address,
                 final Barrio neighborhood, final List<Long> imageIds, final BigDecimal price, final Integer maxCapacity,
                 final String creatorEmail) {
        this.title = title;
        this.description = description;
        this.eventDate = eventDate;
        this.address = address;
        this.neighborhood = neighborhood;
        setImageIds(imageIds);
        this.price = price;
        this.maxCapacity = maxCapacity;
        this.creatorEmail = creatorEmail;
    }

    public Long getId() { return id; }
    public void setId(final Long id) { this.id = id; }
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
    // (tarjetas, "Mis eventos", favoritos, inscripciones). Sale de coverImageId (calculado por
    // la base), asi que NO carga la lista de fotos; getImageIds() (mas abajo) es la galeria
    // completa, para el detalle.
    public Long getImageId() { return coverImageId; }
    public List<Long> getImageIds() { return imageIds; }
    // Copia la lista que llega (no la guarda tal cual): si fuera la lista de OTRO evento
    // (ver EventServiceImpl.update()), vaciar una vaciaria la otra.
    public void setImageIds(final List<Long> imageIds) {
        this.imageIds = imageIds == null ? new ArrayList<>() : new ArrayList<>(imageIds);
    }
    public BigDecimal getPrice() { return price; }
    public void setPrice(final BigDecimal price) { this.price = price; }
    public Integer getMaxCapacity() { return maxCapacity; }
    public void setMaxCapacity(final Integer maxCapacity) { this.maxCapacity = maxCapacity; }
    public String getCreatorEmail() { return creatorEmail; }
    public void setCreatorEmail(final String creatorEmail) { this.creatorEmail = creatorEmail; }
    public User getCreator() { return creator; }
    public void setCreator(final User creator) { this.creator = creator; }
    // Atajo para no escribir getCreator().getId(); null si el evento no tiene dueno cargado.
    // No dispara ninguna consulta: el id ya esta en la fila del evento.
    public Long getCreatorId() { return creator == null ? null : creator.getId(); }
    // Las categorias del evento, ordenadas por nombre (lo que mostraban las pantallas).
    // Es una copia: para agregar o sacar se usan addCategory()/removeCategory().
    public List<EventCategory> getCategories() {
        return categories.stream()
                .sorted(Comparator.comparing(EventCategory::getCategoryName, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }
    public void addCategory(final EventCategory category) {
        categories.add(category);
    }
    public void removeCategory(final EventCategory category) {
        categories.remove(category);
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
    public boolean isFeatured() { return featured; }
    public void setFeatured(final boolean featured) { this.featured = featured; }
    public Date getFeaturedAt() { return featuredAt; }
    public void setFeaturedAt(final Date featuredAt) { this.featuredAt = featuredAt; }
    public Date getReminderSentAt() { return reminderSentAt; }
    public void setReminderSentAt(final Date reminderSentAt) { this.reminderSentAt = reminderSentAt; }

    // Los dos vienen juntos o ninguno (ver EventController): si falta uno el
    // otro no sirve para nada, mejor no mostrar un pin a medias. "is..." (no
    // "has...") a proposito: es el prefijo que la EL de los JSP reconoce como
    // getter de un boolean (${event.locationPinned}), mismo criterio que
    // isPrivate()/isFree()/isCancelled() aca arriba.
    public boolean isLocationPinned() { return latitude != null && longitude != null; }

    public boolean isFree() { return price == null || price.signum() == 0; }
    public boolean isPrivate() { return visibility == EventVisibility.PRIVATE; }
    public boolean isCancelled() { return cancelledAt != null; }

    // Ya paso: la fecha del evento quedo en el pasado. No mira cancelledAt a
    // proposito -- un evento cancelado antes de su fecha no esta "finalizado"
    // en este sentido, solo cancelado (son carteles distintos en el detalle).
    public boolean isFinished() {
        return eventDate.before(new Date());
    }

    // Pasado o cancelado ya no recibe preguntas nuevas.
    public boolean isOpenForQuestions() {
        return !isCancelled() && eventDate.after(new Date());
    }
}
