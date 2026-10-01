package ar.edu.itba.paw.webapp.util;

import java.util.ArrayList;
import java.util.List;

// Todo lo generico de paginacion que usan los controllers (parsear el numero
// de pagina de la URL, armar los numeros a mostrar en el paginador): no es
// logica de negocio ni depende de ningun form en particular, asi que no tiene
// que repetirse ni vivir adentro de un controller especifico.
public final class Paging {

    private Paging() {
    }

    // page=0, page=-1, page=abc caen a la primera pagina. Un page mas alla del
    // total no se corrige aca (no se sabe el total todavia): lo ajusta el service.
    public static int parsePage(final String value) {
        if (value == null || value.isEmpty()) {
            return 1;
        }
        try {
            final int parsed = Integer.parseInt(value);
            return parsed < 1 ? 1 : parsed;
        } catch (final NumberFormatException e) {
            return 1;
        }
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
