package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.exception.InvalidImageException;
import ar.edu.itba.paw.model.exception.UserNotFoundException;
import ar.edu.itba.paw.services.UpdateBioResult;
import ar.edu.itba.paw.services.UserProfile;
import ar.edu.itba.paw.services.UserProfileService;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.form.BioForm;
import ar.edu.itba.paw.webapp.util.Paging;
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

    private final UserProfileService userProfileService;
    private final UserService userService;

    @Autowired
    public UserProfileController(final UserProfileService userProfileService, final UserService userService) {
        this.userProfileService = userProfileService;
        this.userService = userService;
    }

    // Cada grupo de eventos tiene su propio paginado (proximosPage / realizadosPage). Una cuenta que no existe o que no
    // se puede mostrar (suspendida, sin confirmar) da el mismo 404 que cualquier otra cosa que no se encuentra.
    @RequestMapping(value = "/usuarios/{id:[0-9]+}", method = RequestMethod.GET)
    public ModelAndView profile(@PathVariable final long id,
                                @RequestParam(value = "proximosPage", required = false) final String upcomingPage,
                                @RequestParam(value = "realizadosPage", required = false) final String pastPage,
                                @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final Long viewerId = currentUser == null ? null : currentUser.getId();
        final UserProfile profile = userProfileService
                .findProfile(id, viewerId, Paging.parsePage(upcomingPage), Paging.parsePage(pastPage))
                .orElseThrow(() -> new UserNotFoundException(id));

        final ModelAndView mav = new ModelAndView("users/profile");
        mav.addObject("profile", profile);
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
    // (vacio, enorme, no es una imagen) no se cambia nada y se avisa por que.
    @RequestMapping(value = "/perfil/portada", method = RequestMethod.POST)
    public ModelAndView updateCover(@RequestParam(value = "cover", required = false) final MultipartFile cover,
                                    @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                                    final RedirectAttributes redirectAttributes) throws IOException {
        String result;
        try {
            result = cover == null
                    ? InvalidImageException.Reason.EMPTY.name()
                    : userService.updateCover(currentUser.getId(), cover.getBytes(), cover.getContentType(),
                            cover.getOriginalFilename()).name();
        } catch (final InvalidImageException e) {
            result = e.getReason().name();
        }
        redirectAttributes.addFlashAttribute("coverMessage", result);
        return new ModelAndView("redirect:/usuarios/" + currentUser.getId());
    }

    // Quitar la propia portada: el perfil vuelve a la portada por defecto.
    @RequestMapping(value = "/perfil/portada/quitar", method = RequestMethod.POST)
    public ModelAndView removeCover(@ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                                    final RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("coverMessage", userService.removeCover(currentUser.getId()).name());
        return new ModelAndView("redirect:/usuarios/" + currentUser.getId());
    }
}
