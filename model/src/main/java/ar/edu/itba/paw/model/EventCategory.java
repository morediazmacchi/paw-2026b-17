package ar.edu.itba.paw.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

// Una categoria elegida para un evento: una fila de event_category. Es la entidad "intermedia" de
// la relacion muchos-a-muchos entre Event y Category, y existe (en vez de un simple @ManyToMany)
// porque la fila lleva un dato propio: extraInfo, el texto libre que el creador carga para ESA
// categoria en ESE evento. La clave primaria es la pareja (evento, categoria): por eso las dos
// relaciones llevan @Id y EventCategory.Key (@IdClass) describe esa pareja con los ids.
@Entity
@Table(name = "event_category")
@IdClass(EventCategory.Key.class)
public class EventCategory {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "extra_info", length = 500)
    private String extraInfo;

    /* default */ EventCategory() {
        // Solo para Hibernate
    }

    public EventCategory(final Event event, final Category category, final String extraInfo) {
        this.event = event;
        this.category = category;
        this.extraInfo = extraInfo;
    }

    public Event getEvent() { return event; }
    public Category getCategory() { return category; }
    public String getExtraInfo() { return extraInfo; }
    public void setExtraInfo(final String extraInfo) { this.extraInfo = extraInfo; }

    // Atajos que usan los JSP (${ec.categoryName}) y los formularios.
    public long getCategoryId() { return category.getId(); }
    public String getCategoryName() { return category.getName(); }

    // Clave compuesta (@IdClass): mismos nombres que los @Id de arriba y, como tipo, el del id
    // de la entidad a la que apuntan (Long).
    public static class Key implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long event;
        private Long category;

        public Key() {
        }

        public Key(final Long event, final Long category) {
            this.event = event;
            this.category = category;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key)) {
                return false;
            }
            final Key other = (Key) o;
            return Objects.equals(event, other.event) && Objects.equals(category, other.category);
        }

        @Override
        public int hashCode() {
            return Objects.hash(event, category);
        }
    }
}
