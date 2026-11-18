package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.ProfileTab;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.exception.InvalidImageException;
import ar.edu.itba.paw.model.exception.UserNotFoundException;
import ar.edu.itba.paw.services.UpdateBioResult;
import ar.edu.itba.paw.services.UserProfile;
import ar.edu.itba.paw.services.FollowService;
import ar.edu.itba.paw.services.UserProfileService;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.form.BioForm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.IOException;

// Perfil publico de una persona: nombre, biografia, promedio de estrellas y los eventos que organizo (proximos y
// realizados). Lo puede ver cualquiera, incluso sin sesion (ver WebAuthConfig); la biografia la edita la propia
// persona desde su perfil.
@Controller
public class UserProfileController {

    // El mensaje (profile.public.cover.flash.UNREADABLE) cuando el servidor no pudo leer el archivo subido.
    private static final String COVER_UNREADABLE = "UNREADABLE";

    private final UserProfileService userProfileService;
    private final UserService userService;
    private final FollowService followService;

    @Autowired
    public UserProfileController(final UserProfileService userProfileService, final UserService userService,
                                 final FollowService followService) {
        this.userProfileService = userProfileService;
        this.userService = userService;
        this.followService = followService;
    }

    // Seguir a esta persona desde su perfil publico (pide sesion: WebAuthConfig solo deja libre el GET del perfil).
    // Uno mismo, o una cuenta que no se puede mostrar, no se siguen: lo decide el service (y no cambia nada).
    @RequestMapping(value = "/usuarios/{id:[0-9]+}/seguir", method = RequestMethod.POST)
    public ModelAndView follow(@PathVariable final long id,
                               @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        followService.follow(currentUser.getId(), id);
        return new ModelAndView("redirect:/usuarios/" + id);
    }

    @RequestMapping(value = "/usuarios/{id:[0-9]+}/dejar-de-seguir", method = RequestMethod.POST)
    public ModelAndView unfollow(@PathVariable final long id,
                                 @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        followService.unfollow(currentUser.getId(), id);
        return new ModelAndView("redirect:/usuarios/" + id);
    }

    // Los eventos se ven en pestañas (?tab=): se pide y se pagina (?page=) solo la que se muestra; sin elegir ninguna
    // decide el service. Una cuenta que no existe o que no se puede mostrar (suspendida, sin confirmar) da el mismo 404
    // que cualquier otra cosa que no se encuentra.
    @RequestMapping(value = "/usuarios/{id:[0-9]+}", method = RequestMethod.GET)
    public ModelAndView profile(@PathVariable final long id,
                                @RequestParam(value = "tab", required = false) final String tab,
                                @RequestParam(value = "page", defaultValue = "1") final int page,
                                @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final Long viewerId = currentUser == null ? null : currentUser.getId();
        final UserProfile profile = userProfileService
                .findProfile(id, viewerId, ProfileTab.fromParam(tab), page)
                .orElseThrow(() -> new UserNotFoundException(id));

        final ModelAndView mav = new ModelAndView("users/profile");
        mav.addObject("profile", profile);
        // El largo maximo de la biografia: lo usa el formulario de editarla (el limite es del service).
        mav.addObject("maxBioLength", UserService.MAX_BIO_LENGTH);
        return mav;
    }

    // Editar la propia biografia: solo se toca la de quien esta logueado (el id sale de la sesion, no de la URL).
    @RequestMapping(value = "/perfil/biografia", method = RequestMethod.POST)
    public ModelAndView updateBio(@Valid final BioForm form, final BindingResult errors,
                                  @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                                  final RedirectAttributes redirectAttributes) {
        final UpdateBioResult result = errors.hasErrors()
                ? UpdateBioResult.INVALID
                : userService.updateBio(currentUser.getId(), form.getBio());
        redirectAttributes.addFlashAttribute("bioMessage", result.name());
        return new ModelAndView("redirect:/usuarios/" + currentUser.getId());
    }

    // Cambiar la propia foto de portada: igual que la biografia, el id sale de la sesion. Si el archivo no sirve
    // (vacio, enorme, no es una imagen, o no se pudo leer) no se cambia nada y se avisa por que.
    @RequestMapping(value = "/perfil/portada", method = RequestMethod.POST)
    public ModelAndView updateCover(@RequestParam("cover") final MultipartFile cover,
                                    @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                                    final RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("coverMessage", coverMessageOf(cover, currentUser.getId()));
        return new ModelAndView("redirect:/usuarios/" + currentUser.getId());
    }

    private String coverMessageOf(final MultipartFile cover, final long userId) {
        try {
            return userService.updateCover(userId, cover.getBytes(), cover.getContentType(),
                    cover.getOriginalFilename()).name();
        } catch (final InvalidImageException e) {
            return e.getReason().name();
        } catch (final IOException e) {
            return COVER_UNREADABLE;
        }
    }

    // Quitar la propia portada: el perfil vuelve a la portada por defecto.
    @RequestMapping(value = "/perfil/portada/quitar", method = RequestMethod.POST)
    public ModelAndView removeCover(@ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                                    final RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("coverMessage", userService.removeCover(currentUser.getId()).name());
        return new ModelAndView("redirect:/usuarios/" + currentUser.getId());
    }
}
