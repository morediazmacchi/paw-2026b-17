package ar.edu.itba.paw.webapp.auth;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.lenient;

@RunWith(MockitoJUnitRunner.class)
public class AccessDeniedAsNotFoundHandlerTest {

    @Mock
    private HttpServletRequest request;

    // El forward de la pagina generica de "no encontrado" tiene que ir como GET sea cual sea el verbo original.
    @Test
    public void testTheForwardedRequestIsAGetWhateverTheOriginalVerbWas() {
        // Arrange: el pedido original es un PUT (senuelo: el wrapper no tiene que mirarlo)
        lenient().when(request.getMethod()).thenReturn("PUT");

        // Act
        final String method = new AccessDeniedAsNotFoundHandler.AsGetRequest(request).getMethod();

        // Assert
        assertEquals("GET", method);
    }
}
