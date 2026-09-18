// Evita open-redirect: solo rutas propias de la app (empiezan con "/", nunca
// con "//" -- protocol-relative, el navegador la trata como externa igual --
// ni contienen "://"). Usado donde el destino post-accion viene de un
// parametro que no controlamos (login con ?redirect=, favoritos con
// ?returnTo=): sin este chequeo alguien podria armar un link que despues de
// loguearte/tocar un boton te mande a un sitio ajeno.
package ar.edu.itba.paw.webapp.util;

public final class SafeRedirect {

    private SafeRedirect() {
    }

    public static boolean isSafe(final String path) {
        return path != null && path.startsWith("/") && !path.startsWith("//") && !path.contains("://");
    }
}
