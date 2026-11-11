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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "event")
public class Event implements EventAccessData {

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
    // fetch = LAZY a proposito (@ElementCollection es EAGER por default si no se especifica):
    // findByIds() ya NO trae esta galeria (ver su comentario, "las fotos no se traen"), pero sin
    // este fetch explicito Hibernate la cargaba igual, con un SELECT aparte por cada evento de la
    // pagina. Con LAZY, el unico lugar que de verdad necesita la galeria completa (detail.jsp,
    // dentro del mismo request: ver OpenEntityManagerInViewFilter en web.xml) la carga bajo demanda.
    @ElementCollection(fetch = FetchType.LAZY)
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
    // Revision previa a publicarse (distinto de requiresApproval, que es el
    // organizador aceptando INSCRIPCIONES, y distinto del sistema de reportes
    // de ModerationService, que es sobre eventos YA publicados). PENDING por
    // default: un "new Event(...)" sin tocar este campo (tests, services)
    // arranca sin aprobar, igual que arrancaria uno creado de verdad. No va
    // en el constructor, mismo motivo que requiresApproval/visibility: no
    // romper los call sites existentes.
    @Column(name = "approval_status", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private EventApprovalStatus approvalStatus = EventApprovalStatus.PENDING;
    // Motivo que cargo el admin al rechazarlo (ver AdminController). Null si
    // todavia esta pendiente o ya fue aprobado.
    @Column(name = "rejected_reason", length = 500)
    private String rejectedReason;
    // Mensaje del admin al pedir cambios (ver EventService.requestChanges()): distinto de
    // rejectedReason, que es definitivo. No se borra al volver a PENDING (ver update()): el
    // proximo admin que lo revise ve que ya se le pidio algo antes.
    @Column(name = "changes_requested_message", length = 1000)
    private String changesRequestedMessage;
    @Column(name = "changes_requested_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date changesRequestedAt;
    // Cuando un admin lo aprobo (lo pone EventJpaDao.approve()): desde ahi es visible para todos. Lo usan las
    // busquedas guardadas (SavedSearch) para saber que eventos son NUEVOS desde el ultimo aviso. Null en los
    // aprobados antes de que existiera esta columna: para ninguna busqueda guardada son nuevos.
    @Column(name = "approved_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date approvedAt;
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

    @Override
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
    @Override
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
    public EventApprovalStatus getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(final EventApprovalStatus approvalStatus) { this.approvalStatus = approvalStatus; }
    public String getRejectedReason() { return rejectedReason; }
    public void setRejectedReason(final String rejectedReason) { this.rejectedReason = rejectedReason; }
    public String getChangesRequestedMessage() { return changesRequestedMessage; }
    public void setChangesRequestedMessage(final String changesRequestedMessage) { this.changesRequestedMessage = changesRequestedMessage; }
    public Date getChangesRequestedAt() { return changesRequestedAt; }
    public void setChangesRequestedAt(final Date changesRequestedAt) { this.changesRequestedAt = changesRequestedAt; }
    public Date getApprovedAt() { return approvedAt; }
    public void setApprovedAt(final Date approvedAt) { this.approvedAt = approvedAt; }
    public Date getReminderSentAt() { return reminderSentAt; }
    public void setReminderSentAt(final Date reminderSentAt) { this.reminderSentAt = reminderSentAt; }

    // Los dos vienen juntos o ninguno (ver EventController): si falta uno el
    // otro no sirve para nada, mejor no mostrar un pin a medias. "is..." (no
    // "has...") a proposito: es el prefijo que la EL de los JSP reconoce como
    // getter de un boolean (${event.locationPinned}), mismo criterio que
    // isPrivate()/isFree()/isCancelled() aca arriba.
    public boolean isLocationPinned() { return latitude != null && longitude != null; }

    public boolean isFree() { return price == null || price.signum() == 0; }
    @Override
    public boolean isPrivate() { return visibility == EventVisibility.PRIVATE; }
    public boolean isCancelled() { return cancelledAt != null; }
    @Override
    public boolean isApproved() { return approvalStatus == EventApprovalStatus.APPROVED; }
    public boolean isPendingApproval() { return approvalStatus == EventApprovalStatus.PENDING; }
    public boolean isRejected() { return approvalStatus == EventApprovalStatus.REJECTED; }
    public boolean isChangesRequested() { return approvalStatus == EventApprovalStatus.CHANGES_REQUESTED; }

    // Ya paso: la fecha del evento quedo en el pasado. No mira cancelledAt a
    // proposito -- un evento cancelado antes de su fecha no esta "finalizado"
    // en este sentido, solo cancelado (son carteles distintos en el detalle).
    public boolean isFinished() {
        return eventDate.before(new Date());
    }

    // Es un evento privado: solo se ve y se anota con invitacion. Existe porque "private" es palabra reservada
    // en EL (no se puede escribir ${event.private} en una JSP).
    public boolean isInviteOnly() { return isPrivate(); }

    // En que estado queda quien se anota a este evento, segun este lleno (para esa persona) y si tiene una
    // invitacion sin usar. Es la UNICA definicion: la usan register() (para crear la inscripcion) y el detalle (para
    // mostrar de antemano lo que va a pasar). Lleno -> lista de espera; con invitacion -> confirmada (su lugar ya
    // estaba reservado y el organizador ya la eligio); si el evento pide aprobacion -> pendiente; si no, confirmada.
    public RegistrationStatus registrationStatusFor(final boolean full, final boolean invited) {
        if (full) {
            return RegistrationStatus.WAITLISTED;
        }
        if (invited) {
            return RegistrationStatus.CONFIRMED;
        }
        return isRequiresApproval() ? RegistrationStatus.PENDING : RegistrationStatus.CONFIRMED;
    }

    // Vigente: ni cancelado ni pasado. Es lo que habilita unirse al chat, darse de baja, etc.
    public boolean isActive() {
        return !isCancelled() && !isFinished();
    }

    // Se puede editar: ni cancelado (uno cancelado no se edita) ni rechazado (el rechazo es definitivo, no
    // hay reenvio a revision). Un evento pendiente de aprobacion si se edita.
    public boolean isEditable() {
        return !isCancelled() && !isRejected();
    }

    // Se puede reseñar: ya paso y no se cancelo (uno cancelado no sucedio). Falta ademas que quien reseña haya
    // tenido su lugar confirmado y no haya reseñado ya (ver ReviewService.create()).
    public boolean isReviewable() {
        return isFinished() && !isCancelled();
    }

    // Pasado o cancelado ya no recibe preguntas nuevas.
    public boolean isOpenForQuestions() {
        return !isCancelled() && eventDate.after(new Date());
    }
}
