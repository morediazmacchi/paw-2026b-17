package ar.edu.itba.paw.model;

import java.util.Date;

public class Inscripcion {

    public static final String ESTADO_CONFIRMADA = "confirmada";
    public static final String ESTADO_ESPERA = "espera";

    private long id;
    private long eventoId;
    private String email;
    private String nombre;
    private Date fechaInscripcion;
    private String estado;

    public Inscripcion() {
    }

    public Inscripcion(final long eventoId, final String email, final String nombre, final String estado) {
        this.eventoId = eventoId;
        this.email = email;
        this.nombre = nombre;
        this.estado = estado;
    }

    public long getId() { return id; }
    public void setId(final long id) { this.id = id; }
    public long getEventoId() { return eventoId; }
    public void setEventoId(final long eventoId) { this.eventoId = eventoId; }
    public String getEmail() { return email; }
    public void setEmail(final String email) { this.email = email; }
    public String getNombre() { return nombre; }
    public void setNombre(final String nombre) { this.nombre = nombre; }
    public Date getFechaInscripcion() { return fechaInscripcion; }
    public void setFechaInscripcion(final Date fechaInscripcion) { this.fechaInscripcion = fechaInscripcion; }
    public String getEstado() { return estado; }
    public void setEstado(final String estado) { this.estado = estado; }
}
