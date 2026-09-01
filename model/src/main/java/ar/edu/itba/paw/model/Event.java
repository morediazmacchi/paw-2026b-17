package ar.edu.itba.paw.model;

import java.util.Date;

public class Event {

    private long id;
    private String titulo;
    private String descripcion;
    private Date fechaEvento;
    private String direccion;
    private String barrio;
    private String imagenUrl;
    private double precio;
    private Integer cupoMaximo;
    private String creadorEmail;
    private long categoriaId;
    private String categoriaNombre;
    private Date fechaCreacion;

    public Event() {
    }

    public Event(final String titulo, final String descripcion, final Date fechaEvento, final String direccion,
                 final String barrio, final String imagenUrl, final double precio, final Integer cupoMaximo,
                 final String creadorEmail, final long categoriaId) {
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.fechaEvento = fechaEvento;
        this.direccion = direccion;
        this.barrio = barrio;
        this.imagenUrl = imagenUrl;
        this.precio = precio;
        this.cupoMaximo = cupoMaximo;
        this.creadorEmail = creadorEmail;
        this.categoriaId = categoriaId;
    }

    public long getId() { return id; }
    public void setId(final long id) { this.id = id; }
    public String getTitulo() { return titulo; }
    public void setTitulo(final String titulo) { this.titulo = titulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(final String descripcion) { this.descripcion = descripcion; }
    public Date getFechaEvento() { return fechaEvento; }
    public void setFechaEvento(final Date fechaEvento) { this.fechaEvento = fechaEvento; }
    public String getDireccion() { return direccion; }
    public void setDireccion(final String direccion) { this.direccion = direccion; }
    public String getBarrio() { return barrio; }
    public void setBarrio(final String barrio) { this.barrio = barrio; }
    public String getImagenUrl() { return imagenUrl; }
    public void setImagenUrl(final String imagenUrl) { this.imagenUrl = imagenUrl; }
    public double getPrecio() { return precio; }
    public void setPrecio(final double precio) { this.precio = precio; }
    public Integer getCupoMaximo() { return cupoMaximo; }
    public void setCupoMaximo(final Integer cupoMaximo) { this.cupoMaximo = cupoMaximo; }
    public String getCreadorEmail() { return creadorEmail; }
    public void setCreadorEmail(final String creadorEmail) { this.creadorEmail = creadorEmail; }
    public long getCategoriaId() { return categoriaId; }
    public void setCategoriaId(final long categoriaId) { this.categoriaId = categoriaId; }
    public String getCategoriaNombre() { return categoriaNombre; }
    public void setCategoriaNombre(final String categoriaNombre) { this.categoriaNombre = categoriaNombre; }
    public Date getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(final Date fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public boolean isGratis() { return precio == 0; }
}
