// DTO de entrada para crear o editar un evento (ver EventForm.toDTO(), EventService.create() y update()).
// No es parte del modelo de dominio (Event), pero vive en este modulo porque lo ven los tres: webapp lo arma
// desde el form, services lo valida y persistence arma el Event con estos datos (EventDao.create() y update()).
// Solo trae datos: el Event lo construye el DAO, no el service.
package ar.edu.itba.paw.dto;

import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.EventCategoryInfo;
import ar.edu.itba.paw.model.EventVisibility;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class EventDTO {

    private String title;
    private String description;
    private Date eventDate;
    private String address;
    private String addressDetails;
    private Barrio neighborhood;
    // Bytes crudos, todavia sin guardar: el DAO necesita los ids ya resueltos, pero resolverlos
    // (ImageService.create()) es una accion con efecto secundario que tiene que pasar DENTRO de la misma
    // transaccion que crea/actualiza el evento (ver EventServiceImpl), no antes,
    // en el controller -- si no, una imagen subida con exito pero un evento que
    // despues falla quedan huerfanas en la base, sin transaccion que las una.
    private List<NewImageData> newImages = new ArrayList<>();
    private BigDecimal price;
    private Integer maxCapacity;
    private List<EventCategoryInfo> categories = new ArrayList<>();
    private boolean requiresApproval;
    private EventVisibility visibility = EventVisibility.PUBLIC;
    private Double latitude;
    private Double longitude;
    private boolean locationManual = true;
    private boolean parking;

    public String getTitle() { return title; }
    public void setTitle(final String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(final String description) { this.description = description; }

    public Date getEventDate() { return eventDate; }
    public void setEventDate(final Date eventDate) { this.eventDate = eventDate; }

    public String getAddress() { return address; }
    public void setAddress(final String address) { this.address = address; }

    public String getAddressDetails() { return addressDetails; }
    public void setAddressDetails(final String addressDetails) { this.addressDetails = addressDetails; }

    public Barrio getNeighborhood() { return neighborhood; }
    public void setNeighborhood(final Barrio neighborhood) { this.neighborhood = neighborhood; }

    public List<NewImageData> getNewImages() { return newImages; }
    public void setNewImages(final List<NewImageData> newImages) {
        this.newImages = newImages == null ? new ArrayList<>() : newImages;
    }

    public BigDecimal getPrice() { return price; }
    public void setPrice(final BigDecimal price) { this.price = price; }

    public Integer getMaxCapacity() { return maxCapacity; }
    public void setMaxCapacity(final Integer maxCapacity) { this.maxCapacity = maxCapacity; }

    public List<EventCategoryInfo> getCategories() { return categories; }
    public void setCategories(final List<EventCategoryInfo> categories) {
        this.categories = categories == null ? new ArrayList<>() : categories;
    }

    public boolean isRequiresApproval() { return requiresApproval; }
    public void setRequiresApproval(final boolean requiresApproval) { this.requiresApproval = requiresApproval; }

    public EventVisibility getVisibility() { return visibility; }
    public void setVisibility(final EventVisibility visibility) { this.visibility = visibility; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(final Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(final Double longitude) { this.longitude = longitude; }

    public boolean isLocationManual() { return locationManual; }
    public void setLocationManual(final boolean locationManual) { this.locationManual = locationManual; }

    public boolean isParking() { return parking; }
    public void setParking(final boolean parking) { this.parking = parking; }
}
