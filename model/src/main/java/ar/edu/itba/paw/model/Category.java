package ar.edu.itba.paw.model;

public class Category {

    private long id;
    private String name;

    public Category() {
    }

    public Category(final String name) {
        this.name = name;
    }

    public long getId() { return id; }
    public void setId(final long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(final String name) { this.name = name; }
}
