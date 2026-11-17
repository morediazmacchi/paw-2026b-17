package ar.edu.itba.paw.webapp.util;

import java.util.ArrayList;
import java.util.List;

// Todo lo generico de paginacion que usan los controllers (armar los numeros a mostrar en el paginador): no es
// logica de negocio ni depende de ningun form en particular, asi que no tiene que repetirse ni vivir adentro de un
// controller especifico. El numero de pagina de la URL llega ya como int (lo convierte Spring); una pagina fuera de
// rango la ajusta cada service (PageResult.clampPage).
public final class Paging {

    private Paging() {
    }

    // Numeros de pagina a mostrar en el paginador. Con pocas paginas se muestran
    // todas; con muchas, primera + ultima + una ventana alrededor de la actual,
    // con null como marcador de "..." (el JSP lo detecta con <c:if test="${empty p}">).
    public static List<Integer> buildPageNumbers(final int currentPage, final int totalPages) {
        final List<Integer> pages = new ArrayList<>();
        if (totalPages <= 7) {
            for (int i = 1; i <= totalPages; i++) {
                pages.add(i);
            }
            return pages;
        }
        pages.add(1);
        if (currentPage > 3) {
            pages.add(null);
        }
        final int start = Math.max(2, currentPage - 1);
        final int end = Math.min(totalPages - 1, currentPage + 1);
        for (int i = start; i <= end; i++) {
            pages.add(i);
        }
        if (currentPage < totalPages - 2) {
            pages.add(null);
        }
        pages.add(totalPages);
        return pages;
    }
}
