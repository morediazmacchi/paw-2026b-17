package ar.edu.itba.paw.webapp.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UserListLinkTest {

    private static final String LIST = "/admin/usuarios";

    // ---- of() ----

    @Test
    public void testOfWithoutSearchNorPageIsTheCleanList() {
        assertEquals(LIST, UserListLink.of(null, 1));
    }

    @Test
    public void testOfWithBlankSearchIsTheCleanList() {
        assertEquals(LIST, UserListLink.of("   ", 1));
    }

    @Test
    public void testOfKeepsTheSearch() {
        assertEquals(LIST + "?q=ana", UserListLink.of("ana", 1));
    }

    @Test
    public void testOfTrimsTheSearch() {
        assertEquals(LIST + "?q=ana", UserListLink.of("  ana  ", 1));
    }

    @Test
    public void testOfKeepsThePageWhenItIsNotTheFirst() {
        assertEquals(LIST + "?page=3", UserListLink.of(null, 3));
    }

    @Test
    public void testOfKeepsTheSearchAndThePage() {
        assertEquals(LIST + "?q=ana&page=2", UserListLink.of("ana", 2));
    }

    @Test
    public void testOfEncodesTheCharactersThatWouldChangeTheMeaningOfTheQuery() {
        assertEquals(LIST + "?q=a+b%26c%2Bd%40x.com", UserListLink.of("a b&c+d@x.com", 1));
    }

    // ---- safeReturnTo() ----

    @Test
    public void testSafeReturnToOfNothingIsTheCleanList() {
        assertEquals(LIST, UserListLink.safeReturnTo(null));
    }

    @Test
    public void testSafeReturnToKeepsALinkToTheSameScreenWithItsQuery() {
        assertEquals(LIST + "?q=ana&page=2", UserListLink.safeReturnTo(LIST + "?q=ana&page=2"));
    }

    @Test
    public void testSafeReturnToKeepsTheCleanListToo() {
        assertEquals(LIST, UserListLink.safeReturnTo(LIST));
    }

    @Test
    public void testSafeReturnToRejectsAnotherSite() {
        assertEquals(LIST, UserListLink.safeReturnTo("https://evil.com/admin/usuarios"));
    }

    @Test
    public void testSafeReturnToRejectsAProtocolRelativeLink() {
        assertEquals(LIST, UserListLink.safeReturnTo("//evil.com"));
    }

    @Test
    public void testSafeReturnToRejectsAnotherPathOfTheApp() {
        assertEquals(LIST, UserListLink.safeReturnTo("/eventos"));
    }

    @Test
    public void testSafeReturnToRejectsAnActionOfTheSameArea() {
        assertEquals(LIST, UserListLink.safeReturnTo("/admin/usuarios/1/suspender"));
    }

    @Test
    public void testSafeReturnToRejectsAPathThatOnlyStartsLikeTheList() {
        assertEquals(LIST, UserListLink.safeReturnTo("/admin/usuariosX"));
    }

    @Test
    public void testSafeReturnToRejectsBracesBecauseTheRedirectWouldReadThemAsVariables() {
        assertEquals(LIST, UserListLink.safeReturnTo(LIST + "?q={algo}"));
    }
}
