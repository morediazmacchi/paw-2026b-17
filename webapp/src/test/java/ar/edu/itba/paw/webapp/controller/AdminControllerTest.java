package ar.edu.itba.paw.webapp.controller;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AdminControllerTest {

    @Test
    public void testAnAccessDeniedInTheAdminAreaShowsTheForbiddenPage() {
        // Arrange: el handler no usa ninguna dependencia.
        final AdminController controller = new AdminController(null, null, null, null);

        // Act
        final String view = controller.accessDenied().getViewName();

        // Assert
        assertEquals("errors/403", view);
    }
}
