package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.ChatMessage;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.ChatJoinResult;
import ar.edu.itba.paw.services.ChatSendResult;
import ar.edu.itba.paw.services.EventChatService;
import ar.edu.itba.paw.services.EventService;
import ar.edu.itba.paw.webapp.form.ChatMessageForm;
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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

// Grupo de chat de un evento. Sin WebSockets: la pagina pide los mensajes nuevos cada
// pocos segundos (js/event-chat.js) y los recibe como un fragmento de HTML ya escapado.
@Controller
public class EventChatController {

    private static final String XHR_HEADER = "X-Requested-With";

    private final EventChatService chatService;
    private final EventService eventService;

    @Autowired
    public EventChatController(final EventChatService chatService, final EventService eventService) {
        this.chatService = chatService;
        this.eventService = eventService;
    }

    // Unirse al grupo. Si sale bien, directo al chat.
    @RequestMapping(value = "/eventos/{id}/chat/unirse", method = RequestMethod.POST)
    public ModelAndView join(@PathVariable final long id,
                             @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                             final RedirectAttributes redirectAttributes) {
        final ChatJoinResult result = chatService.join(id, currentUser);
        if (result == ChatJoinResult.JOINED || result == ChatJoinResult.ALREADY_MEMBER) {
            return new ModelAndView("redirect:/eventos/" + id + "/chat");
        }
        redirectAttributes.addFlashAttribute("chatMessage", result.name());
        return new ModelAndView("redirect:/eventos/" + id);
    }

    // Salir del grupo (la inscripcion al evento no se toca).
    @RequestMapping(value = "/eventos/{id}/chat/salir", method = RequestMethod.POST)
    public ModelAndView leave(@PathVariable final long id,
                              @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                              final RedirectAttributes redirectAttributes) {
        chatService.leave(id, currentUser.getId());
        redirectAttributes.addFlashAttribute("chatMessage", "LEFT");
        return new ModelAndView("redirect:/eventos/" + id);
    }

    // La pantalla del chat. Quien no es miembro activo (nunca se unio, se dio de baja o
    // lo sacaron) vuelve al detalle del evento.
    @RequestMapping(value = "/eventos/{id}/chat", method = RequestMethod.GET)
    public ModelAndView chat(@PathVariable final long id,
                             @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final Event event = eventService.findByIdOrThrow(id);
        final Optional<List<ChatMessage>> messages = chatService.findMessages(id, currentUser.getId(), 0);
        if (!messages.isPresent()) {
            return new ModelAndView("redirect:/eventos/" + id);
        }
        final ModelAndView mav = new ModelAndView("events/chat");
        mav.addObject("event", event);
        mav.addObject("messages", messages.get());
        mav.addObject("lastId", lastId(messages.get()));
        mav.addObject("currentUserId", currentUser.getId());
        mav.addObject("chatClosed", event.isCancelled() || event.isFinished());
        return mav;
    }

    // Los mensajes posteriores a "despues", como fragmento de HTML (lo pide el JS cada
    // pocos segundos). 403 si ya no es miembro: el JS deja de preguntar.
    @RequestMapping(value = "/eventos/{id}/chat/mensajes", method = RequestMethod.GET)
    public ModelAndView messages(@PathVariable final long id,
                                 @RequestParam(value = "despues", required = false, defaultValue = "0") final long after,
                                 @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                                 final HttpServletResponse response) {
        final Optional<List<ChatMessage>> messages = chatService.findMessages(id, currentUser.getId(), after);
        final ModelAndView mav = new ModelAndView("events/chatMessages");
        mav.addObject("currentUserId", currentUser.getId());
        if (!messages.isPresent()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            mav.addObject("messages", Collections.emptyList());
            return mav;
        }
        mav.addObject("messages", messages.get());
        return mav;
    }

    // Mandar un mensaje. El JS lo manda por XHR y mira solo el codigo de estado
    // (204 ok, 400 invalido, 403 ya no es miembro, 409 el chat se cerro); un POST normal
    // (sin JS) vuelve a la pantalla del chat.
    @RequestMapping(value = "/eventos/{id}/chat/mensajes", method = RequestMethod.POST)
    public ModelAndView send(@PathVariable final long id, @Valid final ChatMessageForm form,
                             final BindingResult errors,
                             @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                             final HttpServletRequest request, final HttpServletResponse response) {
        final ChatSendResult result = errors.hasErrors()
                ? ChatSendResult.INVALID
                : chatService.send(id, currentUser, form.getText());
        if (request.getHeader(XHR_HEADER) == null) {
            return new ModelAndView("redirect:/eventos/" + id + "/chat");
        }
        response.setStatus(statusFor(result));
        return null;
    }

    private static int statusFor(final ChatSendResult result) {
        switch (result) {
            case SENT:
                return HttpServletResponse.SC_NO_CONTENT;
            case INVALID:
                return HttpServletResponse.SC_BAD_REQUEST;
            case CLOSED:
                return HttpServletResponse.SC_CONFLICT;
            default:
                return HttpServletResponse.SC_FORBIDDEN;
        }
    }

    private static long lastId(final List<ChatMessage> messages) {
        return messages.isEmpty() ? 0 : messages.get(messages.size() - 1).getId();
    }
}
