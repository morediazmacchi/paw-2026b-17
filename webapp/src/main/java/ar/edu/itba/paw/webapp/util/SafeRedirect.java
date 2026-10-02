// Evita open-redirect: solo rutas propias de la app (empiezan con "/", nunca
// con "//" -- protocol-relative, el navegador la trata como externa igual --
// ni contienen "://", ni una barra invertida, ni caracteres de control. Los
// navegadores tratan "\" como "/" y descartan tabs y saltos de linea de una
// URL: "/\evil.com" y "/<tab>/evil.com" terminan siendo "//evil.com"). Usado
// donde el destino post-accion viene de un parametro que no controlamos
// (login con ?redirect=, favoritos con ?returnTo=): sin este chequeo alguien
// podria armar un link que despues de loguearte/tocar un boton te mande a un
// sitio ajeno.
package ar.edu.itba.paw.webapp.util;

public final class SafeRedirect {

    private SafeRedirect() {
    }

    public static boolean isSafe(final String path) {
        return path != null && path.startsWith("/") && !path.startsWith("//") && !path.contains("://")
                && !path.contains("\\") && path.chars().allMatch(c -> c >= 0x20);
    }
}
