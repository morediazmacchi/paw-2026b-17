package ar.edu.itba.paw.model;

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
    private Long imageId;
    private double price;
    private Integer maxCapacity;
    private String creatorEmail;
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

    public Event() {
    }

    // El ultimo parametro reemplazo al viejo "long categoryId" unico: ahora un
    // evento puede tener varias categorias, sin informacion extra por
    // categoria (para eso esta setCategories(), usado por el wizard de
    // publicar/editar, que si carga extraInfo por cada una).
    public Event(final String title, final String description, final Date eventDate, final String address,
                 final Barrio neighborhood, final Long imageId, final double price, final Integer maxCapacity,
                 final String creatorEmail, final List<Long> categoryIds) {
        this.title = title;
        this.description = description;
        this.eventDate = eventDate;
        this.address = address;
        this.neighborhood = neighborhood;
        this.imageId = imageId;
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
    public Long getImageId() { return imageId; }
    public void setImageId(final Long imageId) { this.imageId = imageId; }
    public double getPrice() { return price; }
    public void setPrice(final double price) { this.price = price; }
    public Integer getMaxCapacity() { return maxCapacity; }
    public void setMaxCapacity(final Integer maxCapacity) { this.maxCapacity = maxCapacity; }
    public String getCreatorEmail() { return creatorEmail; }
    public void setCreatorEmail(final String creatorEmail) { this.creatorEmail = creatorEmail; }
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

    public boolean isFree() { return price == 0; }
    public boolean isPrivate() { return visibility == EventVisibility.PRIVATE; }
    public boolean isCancelled() { return cancelledAt != null; }
}
