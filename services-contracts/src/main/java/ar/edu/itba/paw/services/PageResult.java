// Una pagina de resultados: el contenido de esta pagina + lo necesario para dibujar un paginador.
package ar.edu.itba.paw.services;

import java.util.List;

public class PageResult<T> {

    private final List<T> content;
    private final long totalElements;
    private final int page;
    private final int pageSize;

    public PageResult(final List<T> content, final long totalElements, final int page, final int pageSize) {
        this.content = content;
        this.totalElements = totalElements;
        this.page = page;
        this.pageSize = pageSize;
    }

    public List<T> getContent() { return content; }
    public long getTotalElements() { return totalElements; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }

    public int getTotalPages() {
        return pageSize <= 0 ? 0 : (int) Math.ceil((double) totalElements / pageSize);
    }

    // Pagina efectiva a pedirle a la base: la pedida si existe, la ultima con
    // resultados si es mayor al total, la 1 si es menor a 1 o si no hay nada.
    // Para quien pagina con SQL (LIMIT/OFFSET): primero cuenta el total
    // (COUNT), despues llama a esto y recien ahi trae las filas de esa pagina.
    // "Mostrando X-Y de Z": el numero (desde 1) del primer y del ultimo elemento de ESTA pagina. 0 y 0 si esta vacia.
    public int getFirstItem() {
        return content.isEmpty() ? 0 : (page - 1) * pageSize + 1;
    }

    public int getLastItem() {
        return content.isEmpty() ? 0 : getFirstItem() + content.size() - 1;
    }

    public static int clampPage(final int page, final long totalElements, final int pageSize) {
        final int totalPages = pageSize <= 0 ? 0 : (int) Math.ceil((double) totalElements / pageSize);
        return totalPages == 0 ? 1 : Math.min(Math.max(page, 1), totalPages);
    }
}
