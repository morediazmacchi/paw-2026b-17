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
    public static int clampPage(final int page, final long totalElements, final int pageSize) {
        final int totalPages = pageSize <= 0 ? 0 : (int) Math.ceil((double) totalElements / pageSize);
        return totalPages == 0 ? 1 : Math.min(Math.max(page, 1), totalPages);
    }

    // Para quien YA tiene la lista completa en memoria porque no sale de una consulta (ver
    // EventSimilarityServiceImpl, que ordena por puntaje calculado): corta el pedazo que corresponde y
    // arma el PageResult, con el mismo criterio que EventServiceImpl.search(). Si la lista sale de la
    // base, NO usar esto: paginar con LIMIT/OFFSET y clampPage().
    public static <T> PageResult<T> of(final List<T> all, final int page, final int pageSize) {
        final int totalElements = all.size();
        final int totalPages = pageSize <= 0 ? 0 : (int) Math.ceil((double) totalElements / pageSize);
        final int effectivePage = totalPages == 0 ? 1 : Math.min(Math.max(page, 1), totalPages);
        final int fromIndex = Math.min((effectivePage - 1) * pageSize, totalElements);
        final int toIndex = Math.min(fromIndex + pageSize, totalElements);
        return new PageResult<>(all.subList(fromIndex, toIndex), totalElements, effectivePage, pageSize);
    }
}
