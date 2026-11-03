package ar.edu.itba.paw.webapp.mail;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WebMailLinksTest {

    private static final String BASE = "http://plancito.test/app";

    private final WebMailLinks links = new WebMailLinks(BASE);

    @Test
    public void testRegistrationsLinkPointsToTheUsersRegistrations() {
        assertEquals(BASE + "/perfil/inscripciones", links.registrations());
    }

    @Test
    public void testEventsLinkPointsToTheListing() {
        assertEquals(BASE + "/eventos", links.events());
    }

    @Test
    public void testEventLinkPointsToTheEventDetail() {
        assertEquals(BASE + "/eventos/12", links.event(12));
    }

    @Test
    public void testEventQuestionsLinkPointsToTheQuestionsSection() {
        assertEquals(BASE + "/eventos/12#preguntas", links.eventQuestions(12));
    }

    @Test
    public void testEditEventLinkPointsToTheEditScreen() {
        assertEquals(BASE + "/eventos/12/editar", links.editEvent(12));
    }

    @Test
    public void testAttendeesLinkPointsToTheOrganizersRegistrantsScreen() {
        assertEquals(BASE + "/perfil/eventos/12/inscriptos", links.attendees(12));
    }

    @Test
    public void testLoginLink() {
        assertEquals(BASE + "/login", links.login());
    }

    @Test
    public void testConfirmAccountLinkWithoutReturnToHasOnlyTheToken() {
        assertEquals(BASE + "/confirmar-cuenta?token=abc", links.confirmAccount("abc", null));
    }

    @Test
    public void testConfirmAccountLinkEncodesTheReturnTo() {
        assertEquals(BASE + "/confirmar-cuenta?token=abc&redirect=%2Feventos%2F5", links.confirmAccount("abc", "/eventos/5"));
    }

    @Test
    public void testResetPasswordLinkCarriesTheToken() {
        assertEquals(BASE + "/login/restablecer?token=xyz", links.resetPassword("xyz"));
    }
}
