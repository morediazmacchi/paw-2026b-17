// Una fila del bloque de categorias del form de publicar/editar evento (una
// por cada categoria existente, en el mismo orden que categoryService.findAll(),
// ver EventController). "seleccionada" es el checkbox; "extraInfo" es el
// texto libre y opcional que solo importa si la categoria esta tildada.
package ar.edu.itba.paw.webapp.form;

import javax.validation.constraints.Size;

public class CategoriaSeleccionForm {

    private Long categoryId;

    private boolean seleccionada;

    @Size(max = 500, message = "La informacion adicional no puede superar los 500 caracteres")
    private String extraInfo;

    public CategoriaSeleccionForm() {
    }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(final Long categoryId) { this.categoryId = categoryId; }

    public boolean isSeleccionada() { return seleccionada; }
    public void setSeleccionada(final boolean seleccionada) { this.seleccionada = seleccionada; }

    public String getExtraInfo() { return extraInfo; }
    public void setExtraInfo(final String extraInfo) { this.extraInfo = extraInfo; }
}
