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

    // Para quien no pagina con SQL (LIMIT/OFFSET) sino que ya tiene la lista
    // completa en memoria (ver UserServiceImpl.findPage()): corta el pedazo que
    // corresponde y arma el PageResult, con el mismo criterio que
    // EventServiceImpl.search() para una pagina pedida que no existe (cae a la
    // ultima con resultados, nunca vacia por eso).
    public static <T> PageResult<T> of(final List<T> all, final int page, final int pageSize) {
        final int totalElements = all.size();
        final int totalPages = pageSize <= 0 ? 0 : (int) Math.ceil((double) totalElements / pageSize);
        final int effectivePage = totalPages == 0 ? 1 : Math.min(Math.max(page, 1), totalPages);
        final int fromIndex = Math.min((effectivePage - 1) * pageSize, totalElements);
        final int toIndex = Math.min(fromIndex + pageSize, totalElements);
        return new PageResult<>(all.subList(fromIndex, toIndex), totalElements, effectivePage, pageSize);
    }
}
