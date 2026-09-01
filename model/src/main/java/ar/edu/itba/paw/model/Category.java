package ar.edu.itba.paw.model;

public class Category {

    private long id;
    private String nombre;

    public Category() {
    }

    public Category(final String nombre) {
        this.nombre = nombre;
    }

    public long getId() { return id; }
    public void setId(final long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(final String nombre) { this.nombre = nombre; }
}
