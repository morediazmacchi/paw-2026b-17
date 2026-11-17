package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.ProfileTab;
import ar.edu.itba.paw.model.PublicUser;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.exception.InvalidImageException;
import ar.edu.itba.paw.model.exception.UserNotFoundException;
import ar.edu.itba.paw.services.PageResult;
import ar.edu.itba.paw.services.UpdateBioResult;
import ar.edu.itba.paw.services.UpdateCoverResult;
import ar.edu.itba.paw.services.UserProfile;
import ar.edu.itba.paw.services.UserProfileService;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.form.BioForm;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios con Mockito. El controller solo arma el modelo y mapea el resultado: el estado observable es la vista
 * devuelta y lo que quedo en el modelo y en los flash attributes.
 */
@RunWith(MockitoJUnitRunner.class)
public class UserProfileControllerTest {

    private static final long USER_ID = 7;
    private static final long VIEWER_ID = 9;

    @Mock
    private UserProfileService userProfileService;
    @Mock
    private UserService userService;

    private UserProfileController controller;

    @Before
    public void setUp() {
        controller = new UserProfileController(userProfileService, userService);
    }

    private static User viewer() {
        final User viewer = new User();
        viewer.setId(VIEWER_ID);
        return viewer;
    }

    private static UserProfile aProfile() {
        return new UserProfile(new PublicUser(USER_ID, "Tomas", null, null), false, ProfileTab.UPCOMING,
                new PageResult<>(Collections.emptyList(), 0, 1, 12), 0, 0, 0, 0, false);
    }

    private static BioForm bio(final String text) {
        final BioForm form = new BioForm();
        form.setBio(text);
        return form;
    }

    @Test
    public void testTheProfileOfAnExistingUserShowsTheProfileView() {
        // Arrange: solo con estos argumentos (el que mira, la pestaña y la pagina pedidas) el service devuelve el perfil
        final UserProfile profile = aProfile();
        when(userProfileService.findProfile(USER_ID, VIEWER_ID, ProfileTab.PAST, 3)).thenReturn(Optional.of(profile));

        // Act
        final ModelAndView result = controller.profile(USER_ID, "PAST", "3", viewer());

        // Assert
        assertEquals("users/profile", result.getViewName());
        assertSame(profile, result.getModel().get("profile"));
    }

    @Test
    public void testAnAnonymousVisitorCanSeeAProfile() {
        // Arrange: sin sesion el que mira es null; sin pestaña pedida va null (la elige el service) y la pagina es la 1
        final UserProfile profile = aProfile();
        when(userProfileService.findProfile(USER_ID, null, null, 1)).thenReturn(Optional.of(profile));

        // Act
        final ModelAndView result = controller.profile(USER_ID, null, null, null);

        // Assert
        assertSame(profile, result.getModel().get("profile"));
    }

    @Test
    public void testATabThatDoesNotExistIsTreatedAsNoTabAndAMalformedPageAsTheFirst() {
        // Arrange: el service solo responde si le llega "ninguna pestaña" y la pagina 1
        final UserProfile profile = aProfile();
        when(userProfileService.findProfile(USER_ID, null, null, 1)).thenReturn(Optional.of(profile));

        // Act
        final ModelAndView result = controller.profile(USER_ID, "inventada", "abc", null);

        // Assert
        assertSame(profile, result.getModel().get("profile"));
    }

    @Test
    public void testTheProfileViewGetsTheMaximumBioLengthForTheEditForm() {
        // Arrange
        when(userProfileService.findProfile(USER_ID, null, null, 1)).thenReturn(Optional.of(aProfile()));

        // Act
        final ModelAndView result = controller.profile(USER_ID, null, null, null);

        // Assert
        assertEquals(UserService.MAX_BIO_LENGTH, result.getModel().get("maxBioLength"));
    }

    @Test(expected = UserNotFoundException.class)
    public void testTheProfileOfAUserThatCannotBeShownIsNotFound() {
        // Arrange
        when(userProfileService.findProfile(USER_ID, null, null, 1)).thenReturn(Optional.empty());

        // Act
        controller.profile(USER_ID, null, null, null);

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert (ErrorAdvice la convierte en un 404).
    }

    @Test
    public void testSavingTheBioRedirectsToTheOwnProfileWithTheResult() {
        // Arrange: el id sale de la sesion, no de lo que mande el form
        when(userService.updateBio(VIEWER_ID, "Hola")).thenReturn(UpdateBioResult.OK);
        final BindingResult noErrors = new BeanPropertyBindingResult(bio("Hola"), "bioForm");
        final RedirectAttributes flash = new RedirectAttributesModelMap();

        // Act
        final ModelAndView result = controller.updateBio(bio("Hola"), noErrors, viewer(), flash);

        // Assert
        assertEquals("redirect:/usuarios/" + VIEWER_ID, result.getViewName());
        assertEquals("OK", flash.getFlashAttributes().get("bioMessage"));
    }

    @Test
    public void testAnInvalidBioFormIsReportedAsInvalidWithoutSaving() {
        // Arrange: el service diria OK; si el controller lo llamara igual, el flash diria OK
        lenient().when(userService.updateBio(VIEWER_ID, "demasiado")).thenReturn(UpdateBioResult.OK);
        final BindingResult withErrors = new BeanPropertyBindingResult(bio("demasiado"), "bioForm");
        withErrors.rejectValue("bio", "Size");
        final RedirectAttributes flash = new RedirectAttributesModelMap();

        // Act
        final ModelAndView result = controller.updateBio(bio("demasiado"), withErrors, viewer(), flash);

        // Assert
        assertEquals("redirect:/usuarios/" + VIEWER_ID, result.getViewName());
        assertEquals("INVALID", flash.getFlashAttributes().get("bioMessage"));
    }

    // ---- portada ----

    private static MockMultipartFile coverFile() {
        return new MockMultipartFile("cover", "portada.png", "image/png", new byte[] {1, 2, 3});
    }

    @Test
    public void testChangingTheCoverRedirectsToTheOwnProfileWithTheResult() throws IOException {
        // Arrange: el id sale de la sesion, no de lo que mande el form
        when(userService.updateCover(VIEWER_ID, new byte[] {1, 2, 3}, "image/png", "portada.png"))
                .thenReturn(UpdateCoverResult.OK);
        final RedirectAttributes flash = new RedirectAttributesModelMap();

        // Act
        final ModelAndView result = controller.updateCover(coverFile(), viewer(), flash);

        // Assert
        assertEquals("redirect:/usuarios/" + VIEWER_ID, result.getViewName());
        assertEquals("OK", flash.getFlashAttributes().get("coverMessage"));
    }

    @Test
    public void testAnInvalidCoverFileIsReportedWithTheReason() throws IOException {
        // Arrange
        when(userService.updateCover(VIEWER_ID, new byte[] {1, 2, 3}, "image/png", "portada.png"))
                .thenThrow(new InvalidImageException(InvalidImageException.Reason.TOO_LARGE));
        final RedirectAttributes flash = new RedirectAttributesModelMap();

        // Act
        final ModelAndView result = controller.updateCover(coverFile(), viewer(), flash);

        // Assert
        assertEquals("redirect:/usuarios/" + VIEWER_ID, result.getViewName());
        assertEquals("TOO_LARGE", flash.getFlashAttributes().get("coverMessage"));
    }

    @Test
    public void testACoverFileThatCannotBeReadIsReportedAsUnreadable() throws IOException {
        // Arrange: el service diria OK; si el controller lo llamara igual, el flash diria OK
        final MultipartFile unreadable = mock(MultipartFile.class);
        when(unreadable.getBytes()).thenThrow(new IOException("disco lleno"));
        lenient().when(userService.updateCover(VIEWER_ID, new byte[0], null, null)).thenReturn(UpdateCoverResult.OK);
        final RedirectAttributes flash = new RedirectAttributesModelMap();

        // Act
        controller.updateCover(unreadable, viewer(), flash);

        // Assert
        assertEquals("UNREADABLE", flash.getFlashAttributes().get("coverMessage"));
    }

    @Test
    public void testRemovingTheCoverRedirectsToTheOwnProfileWithTheResult() {
        // Arrange
        when(userService.removeCover(VIEWER_ID)).thenReturn(UpdateCoverResult.OK);
        final RedirectAttributes flash = new RedirectAttributesModelMap();

        // Act
        final ModelAndView result = controller.removeCover(viewer(), flash);

        // Assert
        assertEquals("redirect:/usuarios/" + VIEWER_ID, result.getViewName());
        assertEquals("OK", flash.getFlashAttributes().get("coverMessage"));
    }
}
